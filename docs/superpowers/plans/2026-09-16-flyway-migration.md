# Flyway 数据库迁移接管实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 将当前短链接核心表和访问统计表纳入 Flyway 版本管理，兼容已有本地数据库数据，并为后续迁移建立固定约定。

**架构：** 使用 Spring Boot Flyway 自动配置，迁移脚本放在 `classpath:db/migration`。首个 `V1__baseline_schema.sql` 以幂等建表语句描述当前完整结构，配置 `baseline-on-migrate` 和基线版本 0，使已有非空数据库可以安全接入；后续迁移只增加新版本脚本，不再手工执行业务 SQL。

**技术栈：** Java 17、Spring Boot 4、Flyway、MySQL 8、MyBatis-Plus、JUnit 5、Maven。

---

### 任务 1：先增加迁移契约测试

**文件：**

- 新增：`src/test/java/com/lzq/shortlink/migration/FlywayMigrationScriptTest.java`

- [ ] **步骤 1：编写失败测试**

测试从 classpath 读取 `db/migration/V1__baseline_schema.sql`，验证脚本存在且包含 `short_link`、`short_link_visit_event`、`short_link_daily_stat` 三张表，同时验证使用 `CREATE TABLE IF NOT EXISTS`。脚本不存在时测试应明确失败。

```java
package com.lzq.shortlink.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationScriptTest {

    @Test
    void shouldDeclareIdempotentBaselineForCoreTables() throws IOException {
        String script;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V1__baseline_schema.sql")) {
            assertTrue(input != null, "Flyway 基线迁移脚本不存在");
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase();
        }

        assertTrue(script.contains("create table if not exists short_link"));
        assertTrue(script.contains(
                "create table if not exists short_link_visit_event"
        ));
        assertTrue(script.contains(
                "create table if not exists short_link_daily_stat"
        ));
    }
}
```

- [ ] **步骤 2：运行测试确认红灯**

运行：

```powershell
mvn test "-Dtest=FlywayMigrationScriptTest"
```

预期：测试失败，原因是 `db/migration/V1__baseline_schema.sql` 尚不存在；如果失败原因是 Maven 无法下载依赖或本机权限问题，停止继续排查并向用户给出处理指引。

### 任务 2：接入 Flyway 依赖和应用配置

**文件：**

- 修改：`pom.xml`
- 修改：`src/main/resources/application.yaml`
- 新增：`src/main/resources/db/migration/V1__baseline_schema.sql`

- [ ] **步骤 1：增加 Flyway 依赖**

在 `pom.xml` 的运行时依赖中增加：

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

不手工指定版本，使用 Spring Boot 依赖管理，避免 Flyway 与 Spring Boot 版本不兼容。

- [ ] **步骤 2：增加 Flyway 配置**

在 `application.yaml` 的 `spring` 节点增加：

```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 0
    baseline-description: 初始数据库基线
    validate-on-migrate: true
    clean-disabled: true
```

不在版本库提交数据库密码。已有 `application-local.yaml` 继续由本机维护。

- [ ] **步骤 3：创建 V1 基线脚本**

创建 `V1__baseline_schema.sql`，以当前实体和已执行 SQL 为准，包含以下三张表：

```sql
CREATE TABLE IF NOT EXISTS short_link (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    short_code VARCHAR(16) NOT NULL COMMENT '短码',
    original_url VARCHAR(2048) NOT NULL COMMENT '原始链接',
    manage_token CHAR(32) NOT NULL COMMENT '管理凭证',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    expire_at DATETIME NULL COMMENT '过期时间',
    visit_count BIGINT NOT NULL DEFAULT 0 COMMENT '累计访问次数',
    last_visited_at DATETIME NULL COMMENT '最近访问时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_short_code (short_code),
    UNIQUE KEY uk_manage_token (manage_token)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '短链接表';

CREATE TABLE IF NOT EXISTS short_link_visit_event (
    event_id CHAR(32) NOT NULL COMMENT '访问事件唯一标识',
    short_link_id BIGINT UNSIGNED NOT NULL COMMENT '短链接主键',
    visited_at DATETIME NOT NULL COMMENT '访问时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (event_id),
    KEY idx_short_link_id_visited_at (short_link_id, visited_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '短链接访问事件去重表';

CREATE TABLE IF NOT EXISTS short_link_daily_stat (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    short_link_id BIGINT UNSIGNED NOT NULL COMMENT '短链接主键',
    stat_date DATE NOT NULL COMMENT '统计日期',
    pv BIGINT NOT NULL DEFAULT 0 COMMENT '当天访问次数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_short_link_id_stat_date (short_link_id, stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '短链接每日访问统计表';
```

脚本只负责结构，不插入演示数据，不删除已有数据，不创建外键，保持当前消费链路兼容。

- [ ] **步骤 4：运行契约测试确认绿灯**

运行：

```powershell
mvn test "-Dtest=FlywayMigrationScriptTest"
```

预期：测试通过。如果依赖下载、文件权限或 Maven 本地仓库出现问题，记录错误并给出用户处理命令，不修改业务实现绕过。

### 任务 3：增加 Flyway 上下文集成测试

**文件：**

- 新增：`src/test/java/com/lzq/shortlink/migration/FlywayContextTest.java`

- [ ] **步骤 1：编写集成测试**

```java
package com.lzq.shortlink.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class FlywayContextTest {

    @Autowired
    private Flyway flyway;

    @Test
    void shouldExposeSuccessfulFlywayMigration() {
        assertNotNull(flyway.info().current());
        assertTrue(flyway.info().current().getVersion().toString().equals("1"));
    }
}
```

- [ ] **步骤 2：运行集成测试**

运行：

```powershell
mvn test "-Dtest=FlywayContextTest"
```

预期：应用上下文启动并能读取 Flyway 当前版本。如果出现端口占用、MySQL 连接失败、数据库权限不足或 RabbitMQ 连接失败，不继续死磕环境问题，向用户给出对应的本机处理指引。

### 任务 4：更新迁移文档和过程记录

**文件：**

- 修改：`docs/api/统计接口联调.md`（如其中仍引用手工建表，补充 Flyway 启动说明）
- 修改：`docs/短链接系统开发路线.md`
- 修改：`docs/sql/2026-09-16-add-daily-visit-stat.sql`
- 修改：`docs/sql/2026-09-16-add-manage-token.sql`

- [ ] **步骤 1：标记旧 SQL 为历史参考**

在两个旧 SQL 文件顶部增加中文说明：这些脚本已经由 Flyway `V1__baseline_schema.sql` 接管，新环境和后续环境不应重复手工执行；文件仅用于历史核对和数据库结构排查。

- [ ] **步骤 2：更新路线日志**

将过程记录中 Flyway 任务状态从“方案已确认，进入实现”更新为“代码已实现，等待本机迁移验收”，并记录测试命令和人工验收 SQL。

### 任务 5：最终验证与提交

- [ ] **步骤 1：运行迁移脚本契约、上下文和全量测试**

运行：

```powershell
mvn test "-Dtest=FlywayMigrationScriptTest,FlywayContextTest"
mvn test
git diff --check
```

第一条命令用于快速定位迁移问题，第二条命令确认不破坏现有业务。若环境依赖不可用，保留代码改动，报告失败原因和用户需要执行的启动、权限或端口处理步骤。

- [ ] **步骤 2：提交代码**

```powershell
git add pom.xml src/main/resources/application.yaml src/main/resources/db/migration/V1__baseline_schema.sql src/test/java/com/lzq/shortlink/migration/FlywayMigrationScriptTest.java src/test/java/com/lzq/shortlink/migration/FlywayContextTest.java docs/短链接系统开发路线.md docs/sql/2026-09-16-add-daily-visit-stat.sql docs/sql/2026-09-16-add-manage-token.sql docs/superpowers/specs/2026-09-16-flyway-migration-design.md docs/superpowers/plans/2026-09-16-flyway-migration.md
git commit -m "feat: 使用 Flyway 接管数据库迁移"
```

不提交 `application-local.yaml`、密码、IDE 配置和用户已有的其他未跟踪计划文件。
