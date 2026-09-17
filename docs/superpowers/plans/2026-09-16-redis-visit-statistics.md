# Redis 异步访问统计实施计划

> **供代理执行者使用：** 必须使用 `subagent-driven-development`（推荐）或 `executing-plans` 子技能逐任务执行。本计划使用复选框跟踪步骤。

**目标：** 有效短链接跳转写入 Redis 访问增量，并由每分钟定时任务将增量累加到 MySQL。

**架构：** 跳转控制器只在确认短链接有效后调用 `recordVisit`；该方法通过 Redis `INCR` 记录增量。`VisitStatSyncScheduler` 用 `SCAN` 找到计数键并通过 `getAndDelete` 原子取出当前增量，再调用 Mapper 进行 MySQL 原子累加。统计故障不阻断 302。

**技术栈：** Java 17、Spring Boot、Spring Scheduling、Spring Data Redis、MyBatis-Plus、MySQL。

---

## 文件结构

- 修改 `src/main/java/com/lzq/shortlink/ShortLinkApplication.java`：启用 Spring 定时任务。
- 修改 `src/main/java/com/lzq/shortlink/entity/ShortLink.java`：映射统计字段。
- 修改 `src/main/java/com/lzq/shortlink/mapper/ShortLinkMapper.java`：提供访问统计原子累加 SQL。
- 修改 `src/main/java/com/lzq/shortlink/service/ShortLinkService.java`：声明记录访问的方法。
- 修改 `src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`：实现 Redis `INCR`。
- 修改 `src/main/java/com/lzq/shortlink/controller/RedirectController.java`：仅对有效跳转记录访问。
- 创建 `src/main/java/com/lzq/shortlink/scheduler/VisitStatSyncScheduler.java`：每分钟同步 Redis 增量到 MySQL。
- 不新建自动化测试：按用户明确要求，本阶段用 Apifox、Redis CLI 和 MySQL 查询做手动验收；后续测试类适配由助手直接完成。

### 任务 1：扩展 MySQL 表和实体字段

**文件：**
- 修改：MySQL 数据库中的 `short_link` 表。
- 修改：`src/main/java/com/lzq/shortlink/entity/ShortLink.java`。

- [ ] **步骤 1：在数据库客户端执行建字段 SQL**

```sql
ALTER TABLE short_link
    ADD COLUMN visit_count BIGINT NOT NULL DEFAULT 0 COMMENT '累计访问次数' AFTER expire_at,
    ADD COLUMN last_visited_at DATETIME NULL COMMENT '最近访问时间' AFTER visit_count;
```

执行后验证：

```sql
DESCRIBE short_link;
```

预期看到 `visit_count` 和 `last_visited_at` 两列。

- [ ] **步骤 2：在 `ShortLink` 添加字段映射**

在 `expireAt` 字段后加入：

```java
/** 已落库的累计访问次数。 */
private Long visitCount;

/** 最近一次落库的访问时间。 */
private LocalDateTime lastVisitedAt;
```

MyBatis-Plus 的下划线转驼峰配置会自动把 `visit_count` 和 `last_visited_at` 映射到这两个字段。

### 任务 2：记录有效短链接的 Redis 访问增量

**文件：**
- 修改：`src/main/java/com/lzq/shortlink/service/ShortLinkService.java`。
- 修改：`src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`。
- 修改：`src/main/java/com/lzq/shortlink/controller/RedirectController.java`。

- [ ] **步骤 1：在业务接口声明统计方法**

在 `ShortLinkService` 末尾加入：

```java
/**
 * 记录一次有效短链接访问。
 *
 * @param shortCode 被访问的有效短码
 */
void recordVisit(String shortCode);
```

- [ ] **步骤 2：在实现类定义统计键前缀**

在 `ShortLinkServiceImpl` 中、`REDIS_KEY_PREFIX` 下方加入：

```java
private static final String VISIT_COUNT_KEY_PREFIX = "short-link:visit:";
```

- [ ] **步骤 3：在实现类添加 Redis 自增方法**

在 `findAvailableShortLink` 方法后、`generateShortCode` 方法前加入：

```java
/**
 * 记录一次有效短链接访问。
 * Redis 不可用时只记录警告，不影响跳转。
 */
@Override
public void recordVisit(String shortCode) {
    String visitCountKey = VISIT_COUNT_KEY_PREFIX + shortCode;

    try {
        stringRedisTemplate.opsForValue().increment(visitCountKey);
        log.debug("短链接访问计数已写入 Redis，shortCode={}", shortCode);
    } catch (RedisConnectionFailureException exception) {
        log.warn(
                "Redis 访问统计失败，不影响短链接跳转，shortCode={}",
                shortCode,
                exception
        );
    }
}
```

- [ ] **步骤 4：只对有效跳转调用统计方法**

在 `RedirectController#redirect` 中，保留不存在判断；在其后、构造 `ResponseEntity` 前加入：

```java
shortLinkService.recordVisit(shortCode);
```

最终结构应为：

```java
if (shortLink == null) {
    throw new ShortLinkNotFoundException();
}

shortLinkService.recordVisit(shortCode);

return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(shortLink.getOriginalUrl()))
        .build();
```

### 任务 3：创建 Mapper 原子累加方法

**文件：**
- 修改：`src/main/java/com/lzq/shortlink/mapper/ShortLinkMapper.java`。

- [ ] **步骤 1：添加 Mapper 依赖导入**

```java
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
```

- [ ] **步骤 2：在 Mapper 接口中添加累加 SQL**

```java
/**
 * 原子累加访问次数，并更新最近访问时间。
 *
 * @param shortCode 短码
 * @param increment 本批次访问增量
 * @return 更新的记录数
 */
@Update("""
        UPDATE short_link
        SET visit_count = visit_count + #{increment},
            last_visited_at = NOW()
        WHERE short_code = #{shortCode}
        """)
int incrementVisitStatistics(
        @Param("shortCode") String shortCode,
        @Param("increment") long increment
);
```

### 任务 4：创建每分钟同步统计的定时任务

**文件：**
- 修改：`src/main/java/com/lzq/shortlink/ShortLinkApplication.java`。
- 创建：`src/main/java/com/lzq/shortlink/scheduler/VisitStatSyncScheduler.java`。

- [ ] **步骤 1：启用定时任务**

在 `ShortLinkApplication` 添加导入：

```java
import org.springframework.scheduling.annotation.EnableScheduling;
```

并在 `@SpringBootApplication` 下方添加：

```java
@EnableScheduling
```

- [ ] **步骤 2：创建定时任务类**

创建 `scheduler` 包和 `VisitStatSyncScheduler.java`：

```java
package com.lzq.shortlink.scheduler;

import com.lzq.shortlink.mapper.ShortLinkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 将 Redis 中的短链接访问增量定时同步到 MySQL。
 */
@Slf4j
@Component
public class VisitStatSyncScheduler {

    private static final String VISIT_COUNT_KEY_PREFIX = "short-link:visit:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ShortLinkMapper shortLinkMapper;

    public VisitStatSyncScheduler(
            StringRedisTemplate stringRedisTemplate,
            ShortLinkMapper shortLinkMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.shortLinkMapper = shortLinkMapper;
    }

    /** 每分钟同步一次待落库的访问增量。 */
    @Scheduled(fixedDelay = 60_000)
    public void syncVisitStatistics() {
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(VISIT_COUNT_KEY_PREFIX + "*")
                .count(100)
                .build();

        try (Cursor<String> cursor = stringRedisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                String visitCountKey = cursor.next();
                String incrementValue = stringRedisTemplate.opsForValue()
                        .getAndDelete(visitCountKey);

                if (incrementValue == null) {
                    continue;
                }

                long increment = Long.parseLong(incrementValue);

                if (increment <= 0) {
                    continue;
                }

                String shortCode = visitCountKey.substring(
                        VISIT_COUNT_KEY_PREFIX.length()
                );
                int updatedRows = shortLinkMapper.incrementVisitStatistics(
                        shortCode,
                        increment
                );

                if (updatedRows == 0) {
                    log.warn("访问统计落库失败，短码不存在，shortCode={}", shortCode);
                }
            }
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis 访问统计同步失败，将在下一轮重试", exception);
        }
    }
}
```

### 任务 5：手动验收计数与同步

**文件：**
- 不修改文件。

- [ ] **步骤 1：启动应用并获取一个有效短码**

使用已创建的有效短码，例如 `dYSy3G4r`。

- [ ] **步骤 2：访问短链接三次**

在 Apifox 连续请求三次：

```text
GET http://localhost:8080/dYSy3G4r
```

- [ ] **步骤 3：在 Redis 查看未落库增量**

```powershell
docker exec redis redis-cli GET short-link:visit:dYSy3G4r
```

预期输出为 `3`。如果该短码在同步前已有访问，请以执行前后的增量判断。

- [ ] **步骤 4：等待一次定时同步后查询 MySQL**

等待最多 60 秒，在数据库客户端执行：

```sql
SELECT short_code, visit_count, last_visited_at
FROM short_link
WHERE short_code = 'dYSy3G4r';
```

预期：`visit_count` 至少增加 3，`last_visited_at` 不为 `NULL`；Redis 键在同步后不存在或重新从 0 开始累计。

- [ ] **步骤 5：提交访问统计代码**

```powershell
git -C 'D:\短链接\short-link' add -- src/main/java/com/lzq/shortlink/ShortLinkApplication.java src/main/java/com/lzq/shortlink/entity/ShortLink.java src/main/java/com/lzq/shortlink/mapper/ShortLinkMapper.java src/main/java/com/lzq/shortlink/service/ShortLinkService.java src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java src/main/java/com/lzq/shortlink/controller/RedirectController.java src/main/java/com/lzq/shortlink/scheduler/VisitStatSyncScheduler.java
git -C 'D:\短链接\short-link' commit -m "feat: 添加 Redis 异步访问统计"
```
