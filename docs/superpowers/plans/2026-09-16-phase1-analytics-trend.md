# 第一阶段：访问统计与最近 7 天 PV 趋势实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 RabbitMQ 异步访问统计基础上，提供可联调的最近 7 天 PV 趋势接口，并验证跳转、消息消费、幂等和统计查询链路。

**Architecture:** 保留现有“跳转主链路 Redis 增量 + RabbitMQ 访问事件 + 消费端事件去重 + MySQL 每日聚合”的结构。统计接口继续使用当前 `X-Manage-Token` 兼容鉴权，查询 `short_link_daily_stat` 后在应用层补齐零值日期；本阶段不引入用户、工作空间和号段发号，避免跨子系统耦合。

**Tech Stack:** Java 17、Spring Boot 4、Spring MVC、Spring AMQP、MyBatis-Plus、MySQL 8、Redis、RabbitMQ、JUnit 5、Mockito、MockMvc、Maven、Apifox。

---

## 文件结构

- `src/main/java/com/lzq/shortlink/mapper/ShortLinkDailyStatMapper.java`：按短链接和日期范围查询每日统计。
- `src/main/java/com/lzq/shortlink/mapper/ShortLinkDailyStatRow.java`：封装每日统计查询行，避免 Mapper 暴露实体全部字段。
- `src/main/java/com/lzq/shortlink/dto/DailyPvResponse.java`：单日 PV 响应项。
- `src/main/java/com/lzq/shortlink/dto/ShortLinkStatisticsResponse.java`：兼容原有累计统计并增加趋势字段。
- `src/main/java/com/lzq/shortlink/service/ShortLinkAnalyticsService.java`：定义趋势查询接口。
- `src/main/java/com/lzq/shortlink/service/impl/ShortLinkAnalyticsServiceImpl.java`：校验日期、查询数据库并补零。
- `src/main/java/com/lzq/shortlink/controller/ShortLinkController.java`：解析查询参数并组装完整响应。
- `src/main/java/com/lzq/shortlink/exception/InvalidStatisticsRangeException.java`：统计范围错误。
- `src/main/java/com/lzq/shortlink/exception/GlobalExceptionHandler.java`：将范围错误转换为 400 响应。
- `src/test/java/com/lzq/shortlink/service/ShortLinkAnalyticsServiceTest.java`：趋势补零、日期边界和粒度校验。
- `src/test/java/com/lzq/shortlink/controller/ShortLinkControllerTest.java`：管理凭证、默认 7 天和非法参数接口验收。
- `docs/sql/2026-09-16-add-daily-visit-stat.sql`：本阶段使用的统计表结构来源，不在本阶段引入 Flyway。

## Task 1: 固化当前 RabbitMQ 访问统计基线

**Files:**
- Verify: `src/main/java/com/lzq/shortlink/config/RabbitMqConfig.java`
- Verify: `src/main/java/com/lzq/shortlink/message/VisitEventPublisher.java`
- Verify: `src/main/java/com/lzq/shortlink/message/VisitEventConsumer.java`
- Verify: `src/main/java/com/lzq/shortlink/scheduler/VisitStatSyncScheduler.java`
- Verify: `src/test/java/com/lzq/shortlink/config/RabbitMqConfigTest.java`
- Verify: `src/test/java/com/lzq/shortlink/message/VisitEventPublisherTest.java`
- Verify: `src/test/java/com/lzq/shortlink/message/VisitEventConsumerTest.java`

- [ ] **Step 1: 确认数据库表和 RabbitMQ 拓扑**

在 MySQL 执行 `docs/sql/2026-09-16-add-daily-visit-stat.sql` 中尚未执行的建表语句；RabbitMQ 管理台确认存在 `short-link.visit.queue`、`short-link.visit.dlq` 和绑定到 `short-link.events` 的 `link.visit` 路由。

- [ ] **Step 2: 运行当前消息链路测试**

运行：

```powershell
mvn -f short-link/pom.xml test -Dtest=RabbitMqConfigTest,VisitEventPublisherTest,VisitEventConsumerTest
```

预期：3 个测试类编译通过，失败数为 0。若失败，只修复 RabbitMQ 当前工作区改动，不在本任务中引入新的业务行为。

- [ ] **Step 3: 运行一次真实链路验收**

启动 Redis、RabbitMQ、MySQL 和应用，创建一个短链接并访问两次，确认：

```sql
SELECT short_link_id, stat_date, pv
FROM short_link_daily_stat
ORDER BY id DESC
LIMIT 5;
```

预期对应短链接当天 `pv = 2`；重复投递同一个 `event_id` 不会使 PV 再增加；主队列在正常消费后无待处理消息。

- [ ] **Step 4: 提交基线收口**

只提交已经验证的 RabbitMQ 业务文件和测试文件，使用提交信息：

```text
feat: 收口访问事件异步统计链路
```

不得提交真实密码、IDE 配置或与本任务无关的文件。

## Task 2: 增加每日统计范围查询和趋势响应模型

**Files:**
- Modify: `src/main/java/com/lzq/shortlink/mapper/ShortLinkDailyStatMapper.java`
- Create: `src/main/java/com/lzq/shortlink/dto/DailyPvResponse.java`
- Modify: `src/main/java/com/lzq/shortlink/dto/ShortLinkStatisticsResponse.java`
- Test: `src/test/java/com/lzq/shortlink/service/ShortLinkAnalyticsServiceTest.java`

- [ ] **Step 1: 定义单日响应项和趋势字段**

创建以下 record，所有注释使用中文：

```java
package com.lzq.shortlink.dto;

import java.time.LocalDate;

/** 单日访问量趋势项。 */
public record DailyPvResponse(LocalDate date, long pv) {
}
```

在 `ShortLinkStatisticsResponse` 增加：

```java
/** 趋势起始日期。 */
private LocalDate trendFrom;

/** 趋势结束日期。 */
private LocalDate trendTo;

/** 当前统计粒度。 */
private String granularity;

/** 按日期升序排列的每日 PV。 */
private List<DailyPvResponse> pvTrend;
```

同时增加 `import java.time.LocalDate;` 和 `import java.util.List;`。原有 `shortCode`、`originalUrl`、`visitCount`、`lastVisitedAt` 字段保持不变，保证旧客户端仍可读取原字段。

- [ ] **Step 2: 先写范围查询的失败测试**

在 `ShortLinkAnalyticsServiceTest` 中使用 Mockito 构造 Mapper，验证 3 天数据只返回 3 个日期，并对缺失日期补 0。测试类先建立固定的依赖注入结构：

```java
@ExtendWith(MockitoExtension.class)
class ShortLinkAnalyticsServiceTest {

    @Mock
    private ShortLinkDailyStatMapper mapper;

    private ShortLinkAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new ShortLinkAnalyticsServiceImpl(mapper);
    }
}
```

在该测试类中增加：

```java
@Test
void shouldFillMissingDatesWithZeroPv() {
    Long shortLinkId = 59L;
    LocalDate from = LocalDate.of(2026, 9, 14);
    LocalDate to = LocalDate.of(2026, 9, 16);

    when(mapper.selectByShortLinkIdAndDateBetween(shortLinkId, from, to))
            .thenReturn(List.of(
                    new ShortLinkDailyStatRow(
                            LocalDate.of(2026, 9, 16), 2L
                    )
            ));

    List<DailyPvResponse> result = service.queryDailyPv(
            shortLinkId, from, to
    );

    assertEquals(
            List.of(
                    new DailyPvResponse(LocalDate.of(2026, 9, 14), 0L),
                    new DailyPvResponse(LocalDate.of(2026, 9, 15), 0L),
                    new DailyPvResponse(LocalDate.of(2026, 9, 16), 2L)
            ),
            result
    );
}
```

测试中 `ShortLinkDailyStatRow` 是 Mapper 查询使用的轻量 record，下一步一并定义；先运行该测试，预期因接口和实现不存在而编译失败。

- [ ] **Step 3: 增加只读查询模型和 Mapper 方法**

创建 `ShortLinkDailyStatRow.java`：

```java
package com.lzq.shortlink.mapper;

import java.time.LocalDate;

/** 每日统计查询结果。 */
public record ShortLinkDailyStatRow(LocalDate statDate, Long pv) {
}
```

在 `ShortLinkDailyStatMapper` 中增加查询方法：

```java
@Select("""
        SELECT stat_date, pv
        FROM short_link_daily_stat
        WHERE short_link_id = #{shortLinkId}
          AND stat_date BETWEEN #{from} AND #{to}
        ORDER BY stat_date ASC
        """)
List<ShortLinkDailyStatRow> selectByShortLinkIdAndDateBetween(
        @Param("shortLinkId") Long shortLinkId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
);
```

同时补充 `java.util.List`、`org.apache.ibatis.annotations.Select` 等导入；Mapper 方法和 record 的字段名必须与下划线转驼峰映射一致。

- [ ] **Step 4: 运行 Mapper 查询测试**

执行：

```powershell
mvn -f short-link/pom.xml test -Dtest=ShortLinkAnalyticsServiceTest
```

预期：在 Service 尚未实现前失败；确认失败原因是目标行为缺失，而不是 SQL、导入或类型错误。

## Task 3: 实现趋势服务和日期边界规则

**Files:**
- Create: `src/main/java/com/lzq/shortlink/service/ShortLinkAnalyticsService.java`
- Create: `src/main/java/com/lzq/shortlink/service/impl/ShortLinkAnalyticsServiceImpl.java`
- Create: `src/main/java/com/lzq/shortlink/exception/InvalidStatisticsRangeException.java`
- Modify: `src/main/java/com/lzq/shortlink/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/lzq/shortlink/service/ShortLinkAnalyticsServiceTest.java`

- [ ] **Step 1: 定义服务契约**

创建接口：

```java
package com.lzq.shortlink.service;

import com.lzq.shortlink.dto.DailyPvResponse;

import java.time.LocalDate;
import java.util.List;

/** 短链接访问分析服务。 */
public interface ShortLinkAnalyticsService {

    /** 查询指定日期范围的每日 PV，并补齐没有记录的日期。 */
    List<DailyPvResponse> queryDailyPv(
            Long shortLinkId,
            LocalDate from,
            LocalDate to
    );
}
```

异常类：

```java
package com.lzq.shortlink.exception;

/** 统计查询日期或粒度不合法。 */
public class InvalidStatisticsRangeException extends RuntimeException {

    public InvalidStatisticsRangeException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: 写日期边界失败测试**

补充以下测试：

```java
@Test
void shouldRejectReversedDateRange() {
    assertThrows(
            InvalidStatisticsRangeException.class,
            () -> service.queryDailyPv(
                    59L,
                    LocalDate.of(2026, 9, 16),
                    LocalDate.of(2026, 9, 15)
            )
    );
}

@Test
void shouldRejectRangeLongerThanNinetyDays() {
    assertThrows(
            InvalidStatisticsRangeException.class,
            () -> service.queryDailyPv(
                    59L,
                    LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 8, 30)
            )
    );
}
```

日期范围按自然日闭区间计算：`ChronoUnit.DAYS.between(from, to) + 1` 必须在 1 到 90 之间。

- [ ] **Step 3: 实现查询和补零**

`ShortLinkAnalyticsServiceImpl` 使用构造器注入 `ShortLinkDailyStatMapper`：

```java
private final ShortLinkDailyStatMapper mapper;

public ShortLinkAnalyticsServiceImpl(ShortLinkDailyStatMapper mapper) {
    this.mapper = mapper;
}
```

Service 实现必须执行以下逻辑：

```java
if (from.isAfter(to)) {
    throw new InvalidStatisticsRangeException("开始日期不能晚于结束日期");
}
long days = ChronoUnit.DAYS.between(from, to) + 1;
if (days > 90) {
    throw new InvalidStatisticsRangeException("统计范围不能超过 90 天");
}

Map<LocalDate, Long> pvByDate = mapper
        .selectByShortLinkIdAndDateBetween(shortLinkId, from, to)
        .stream()
        .collect(Collectors.toMap(
                ShortLinkDailyStatRow::statDate,
                row -> row.pv() == null ? 0L : row.pv()
        ));

List<DailyPvResponse> result = new ArrayList<>();
for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
    result.add(new DailyPvResponse(date, pvByDate.getOrDefault(date, 0L)));
}
return result;
```

Mapper 返回同一日期重复行时应视为数据完整性问题而失败，不得静默覆盖；数据库的 `(short_link_id, stat_date)` 唯一键必须继续保留。

- [ ] **Step 4: 处理 400 响应**

在 `GlobalExceptionHandler` 增加：

```java
@ExceptionHandler(InvalidStatisticsRangeException.class)
public ResponseEntity<ApiErrorResponse> handleStatisticsRangeException(
        InvalidStatisticsRangeException exception
) {
    return buildResponse(
            HttpStatus.BAD_REQUEST,
            "INVALID_STATISTICS_RANGE",
            exception.getMessage()
    );
}
```

- [ ] **Step 5: 验证服务层**

执行：

```powershell
mvn -f short-link/pom.xml test -Dtest=ShortLinkAnalyticsServiceTest
```

预期：所有趋势补零、日期顺序、反向范围和 90 天上限测试通过。

## Task 4: 扩展统计接口并保持旧客户端兼容

**Files:**
- Modify: `src/main/java/com/lzq/shortlink/controller/ShortLinkController.java`
- Modify: `src/main/java/com/lzq/shortlink/dto/ShortLinkStatisticsResponse.java`
- Test: `src/test/java/com/lzq/shortlink/controller/ShortLinkControllerTest.java`

- [ ] **Step 1: 定义请求参数规则**

保留原接口路径：

```text
GET /api/links/{shortCode}/stats
```

增加可选参数：

```text
from=2026-09-10
to=2026-09-16
granularity=day
```

规则固定为：

- `from` 和 `to` 都不传：使用最近 7 个自然日，包含当天。
- 只传 `from`：查询 `from` 起连续 7 天。
- 只传 `to`：查询 `to` 前 6 天至 `to`。
- 同时传入：按传入闭区间查询。
- `granularity` 省略时为 `day`；任何非 `day` 值返回 400。
- 日期格式不是 `yyyy-MM-dd` 返回 400。
- 范围超过 90 天或开始日期晚于结束日期返回 400。

- [ ] **Step 2: 写接口失败测试**

在现有 `ShortLinkControllerTest` 增加：

```java
@Test
@Transactional
void shouldReturnSevenDayPvTrendByDefault() throws Exception {
    ShortLink shortLink = shortLinkService.createShortLink(
            "https://example.com/trend",
            null
    );

    mockMvc.perform(get("/api/links/{shortCode}/stats", shortLink.getShortCode())
                    .header("X-Manage-Token", shortLink.getManageToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.granularity").value("day"))
            .andExpect(jsonPath("$.pvTrend.length()").value(7))
            .andExpect(jsonPath("$.pvTrend[0].pv").value(0));
}

@Test
void shouldRejectUnsupportedGranularity() throws Exception {
    mockMvc.perform(get("/api/links/abc12345/stats")
                    .queryParam("granularity", "hour")
                    .header("X-Manage-Token", "invalid-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code")
                    .value("INVALID_STATISTICS_RANGE"));
}

@Test
void shouldRejectMoreThanNinetyDays() throws Exception {
    mockMvc.perform(get("/api/links/abc12345/stats")
                    .queryParam("from", "2026-01-01")
                    .queryParam("to", "2026-04-01")
                    .header("X-Manage-Token", "invalid-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code")
                    .value("INVALID_STATISTICS_RANGE"));
}
```

如果控制器当前先校验管理凭证再解析业务参数，则非法参数测试必须使用有效短码和有效管理凭证；测试重点是 400 响应，而不是依赖 404 短码查询顺序。

- [ ] **Step 3: 实现控制器参数解析**

在控制器中新增 `ShortLinkAnalyticsService analyticsService` 构造器依赖，并保留原有 `ShortLinkService shortLinkService` 依赖；补充 `DailyPvResponse`、`InvalidStatisticsRangeException`、`RequestParam`、`DateTimeFormat`、`LocalDate` 和 `List` 导入。

控制器补充 `java.time.temporal.ChronoUnit` 导入，方法签名扩展为：

```java
public ShortLinkStatisticsResponse getStatistics(
        @PathVariable String shortCode,
        @RequestHeader(value = "X-Manage-Token", required = false)
        String manageToken,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,
        @RequestParam(defaultValue = "day") String granularity
) {
    if (!"day".equals(granularity)) {
        throw new InvalidStatisticsRangeException(
                "当前仅支持 day 粒度"
        );
    }

    StatisticsRange range = resolveStatisticsRange(from, to);
    ShortLink shortLink = shortLinkService.findShortLinkForStatistics(
            shortCode,
            manageToken
    );
    if (shortLink == null) {
        throw new ShortLinkNotFoundException();
    }

    List<DailyPvResponse> trend = analyticsService.queryDailyPv(
            shortLink.getId(), range.from(), range.to()
    );

    ShortLinkStatisticsResponse response = new ShortLinkStatisticsResponse();
    response.setShortCode(shortLink.getShortCode());
    response.setOriginalUrl(shortLink.getOriginalUrl());
    response.setVisitCount(shortLink.getVisitCount());
    response.setLastVisitedAt(shortLink.getLastVisitedAt());
    response.setTrendFrom(range.from());
    response.setTrendTo(range.to());
    response.setGranularity(granularity);
    response.setPvTrend(trend);
    return response;
}

private StatisticsRange resolveStatisticsRange(
        LocalDate from,
        LocalDate to
) {
    StatisticsRange range;
    if (from == null && to == null) {
        LocalDate end = LocalDate.now();
        range = new StatisticsRange(end.minusDays(6), end);
    } else if (from != null && to == null) {
        range = new StatisticsRange(from, from.plusDays(6));
    } else if (from == null) {
        range = new StatisticsRange(to.minusDays(6), to);
    } else {
        range = new StatisticsRange(from, to);
    }

    if (range.from().isAfter(range.to())) {
        throw new InvalidStatisticsRangeException(
                "开始日期不能晚于结束日期"
        );
    }
    long days = ChronoUnit.DAYS.between(
            range.from(), range.to()
    ) + 1;
    if (days > 90) {
        throw new InvalidStatisticsRangeException(
                "统计范围不能超过 90 天"
        );
    }
    return range;
}

private record StatisticsRange(LocalDate from, LocalDate to) {
}
```

`resolveStatisticsRange` 必须在查询短链接前执行，使非法粒度、反向日期和超过 90 天的请求稳定返回 400，而不会被无关的 404 短码掩盖。`ShortLinkAnalyticsService` 仍需重复执行日期范围上限校验，防止未来被其他调用方绕过控制器。控制器只负责组合默认范围和校验粒度；通过管理凭证后，使用短链接 ID 查询每日统计。

- [ ] **Step 4: 验证接口兼容性**

执行：

```powershell
mvn -f short-link/pom.xml test -Dtest=ShortLinkControllerTest
```

预期：旧统计字段继续返回；默认新增 7 个 `pvTrend` 项；无效粒度、反向日期和超过 90 天均返回 400；错误管理凭证仍返回原有 404 和 `LINK_NOT_FOUND`。

## Task 5: 联调文档和最终验收

**Files:**
- Modify: `docs/短链接系统开发路线.md`
- Create: `docs/api/统计接口联调.md`

- [ ] **Step 1: 添加 Apifox 联调示例**

在 `docs/api/统计接口联调.md` 写明：

```http
GET http://localhost:8080/api/links/{shortCode}/stats?granularity=day
X-Manage-Token: {manageToken}
```

成功响应至少包含：

```json
{
  "shortCode": "KkwBVo5J",
  "visitCount": 2,
  "lastVisitedAt": "2026-09-16T20:32:24",
  "trendFrom": "2026-09-10",
  "trendTo": "2026-09-16",
  "granularity": "day",
  "pvTrend": [
    {"date": "2026-09-10", "pv": 0},
    {"date": "2026-09-16", "pv": 2}
  ]
}
```

文档必须说明 `visitCount` 是定时同步后的累计值，`pvTrend` 是 RabbitMQ 消费后的每日聚合值，两者允许短暂不一致。

- [ ] **Step 2: 更新开发路线验收项**

在开发路线中补充：默认统计接口返回最近 7 个自然日、趋势日期升序、缺失日期 `pv=0`、最大 90 天、只支持 `day` 粒度。

- [ ] **Step 3: 运行全量测试和代码检查**

执行：

```powershell
mvn -f short-link/pom.xml test
git -C short-link diff --check
```

预期：Maven 构建成功，所有测试失败数为 0，`git diff --check` 无输出。

- [ ] **Step 4: 完成真实接口验收**

使用 Apifox 或 curl 完成以下顺序：

1. 创建短链接并保存响应中的 `shortCode` 和 `manageToken`。
2. 访问短链接两次，等待 RabbitMQ 消费完成。
3. 请求 `/api/links/{shortCode}/stats`，确认返回 7 天趋势且当天 `pv=2`。
4. 请求指定 3 天范围，确认返回 3 项且日期升序。
5. 请求 91 天范围和 `granularity=hour`，确认均返回 400。
6. 使用错误 `X-Manage-Token`，确认返回 404 和 `LINK_NOT_FOUND`。

- [ ] **Step 5: 提交第一阶段实现**

确认测试和真实链路验收通过后，提交本阶段代码、测试和联调文档，提交信息为：

```text
feat: 完成最近七天 PV 趋势统计接口
```

## 自检清单

- 统计查询没有引入短码存在性预查询，RabbitMQ 仍不阻塞 302 跳转。
- 事件去重和每日 PV Upsert 仍由数据库唯一键与事务保障。
- 旧 `/api/links/{shortCode}/stats` 路径和原有响应字段保持兼容。
- 趋势查询只允许闭区间自然日、最大 90 天、`day` 粒度。
- 所有缺失日期由应用补零，结果按日期升序返回。
- 统计错误使用稳定错误码 `INVALID_STATISTICS_RANGE` 和 HTTP 400。
- 本阶段不创建 `short_code_segment` 表，不实现布隆过滤器、Redis 单值自增或号段发号。
- 本阶段不引入用户鉴权和工作空间表；下一阶段再做 SaaS 身份与租户边界。
