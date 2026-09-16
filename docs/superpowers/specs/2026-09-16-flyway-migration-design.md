# Flyway 数据库迁移接管设计

## 1. 目标

将当前通过数据库客户端手工执行的建表和改表脚本，迁移到 Flyway 版本化管理。既有本地数据库中的短链接和访问统计数据必须保留；新数据库可以仅依赖项目启动完成初始化；后续数据库变更必须通过新的 Flyway 迁移脚本提交。

## 2. 当前状态

当前项目已经使用以下表：

- `short_link`：短码、原始链接、管理凭证、过期时间和累计访问统计。
- `short_link_visit_event`：RabbitMQ 访问事件去重。
- `short_link_daily_stat`：每日 PV 聚合。

历史建表和改表脚本位于 `docs/sql`，其中部分脚本已经在本地数据库执行。直接把旧脚本改名为普通 Flyway 迁移，可能因为表已经存在或字段已经存在而失败，因此本次采用可兼容既有数据库的基线迁移。

## 3. 迁移方案

### 3.1 目录与命名

迁移脚本统一放在：

```text
src/main/resources/db/migration/
```

文件命名使用 Flyway 默认格式：

```text
V<版本>__<中文或英文描述>.sql
```

本次新增：

```text
V1__baseline_schema.sql
```

后续字段、索引和表变更从 `V2` 开始递增，禁止修改已经在任何环境执行过的迁移文件。

### 3.2 既有库兼容

`V1__baseline_schema.sql` 使用 `CREATE TABLE IF NOT EXISTS` 定义当前完整表结构。这样：

- 新数据库会创建完整的三张表。
- 已有表和数据不会被删除或覆盖。
- Flyway 会记录迁移历史，后续迁移可以进行校验。

应用配置启用：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 0
    baseline-description: 初始数据库基线
    validate-on-migrate: true
    clean-disabled: true
```

`baseline-on-migrate` 用于已有非空数据库首次接入。基线版本设置为 `0`，因此 `V1` 仍会执行；由于 `V1` 使用幂等建表语句，已有表会被保留，新表会被补齐。

### 3.3 数据安全边界

- 迁移脚本不得包含 `DROP DATABASE`、无条件 `DROP TABLE` 或删除业务数据的语句。
- 生产环境禁止执行 Flyway clean，配置中固定 `clean-disabled: true`。
- 未经结构核对，不自动修复未知环境中的字段漂移；发现字段缺失时给出 SQL 检查和人工处理指引。
- 数据库连接、密码和端口继续由本地配置或环境变量提供，不写入版本库。

## 4. 代码与测试

本阶段只增加 Flyway 依赖、配置、基线迁移脚本和迁移测试，不修改短链接业务行为。测试覆盖：

1. Flyway 自动配置已启用，迁移位置正确。
2. `V1__baseline_schema.sql` 存在且包含三张核心表。
3. 应用上下文在 Flyway 配置下可以启动。
4. Maven 测试不依赖额外端口；如果本机 MySQL 权限或服务不可用，输出明确的人工执行指引。

## 5. 运维执行指引

首次接入已有数据库前，先备份 `short_link`、`short_link_visit_event` 和 `short_link_daily_stat`。启动应用后检查：

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

预期能看到 `version = '1'` 且 `success = 1`。如果遇到端口占用、数据库权限不足或连接失败，不修改业务代码强行绕过，应根据启动日志处理本机环境后重新启动。
