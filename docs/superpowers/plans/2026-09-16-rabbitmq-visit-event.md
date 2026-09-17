# RabbitMQ 访问事件异步化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在单体短链接服务中异步处理有效访问事件，得到可幂等的每日 PV 统计，并保持 302 跳转不受 RabbitMQ 故障影响。

**Architecture:** 跳转成功后先执行既有 Redis 增量计数，再发布仅含事件标识、短链接 ID、短码和访问时间的 RabbitMQ 消息。消费者用事件去重表保证至少一次投递不重复统计，然后在同一事务中原子累加每日 PV；主队列配置 DLQ，消费失败重试三次后死信。

**Tech Stack:** Java 17、Spring Boot 4、Spring AMQP、RabbitMQ 4、MyBatis-Plus、MySQL、Redis、JUnit 5、Mockito。

---

## 文件结构

- `pom.xml`：引入 `spring-boot-starter-amqp`。
- `src/main/resources/application.yaml`：提交无密钥的监听重试配置。
- `src/main/resources/application-local.example.yaml`：安全的本地配置模板。
- `config/RabbitMqConfig.java`：声明交换机、队列、DLQ、绑定和 JSON 转换器。
- `message/VisitEvent.java`、`VisitEventPublisher.java`、`VisitEventConsumer.java`：定义、发布和事务消费访问事件。
- `entity/ShortLinkVisitEvent.java`、`ShortLinkDailyStat.java` 与对应 Mapper：事件去重和每日 PV Upsert。
- `docs/sql/2026-09-16-add-daily-visit-stat.sql`：新建两张统计表。
- `ShortLinkService`、`ShortLinkServiceImpl`、`RedirectController`：把完整 `ShortLink` 传到访问记录方法。

### Task 1: 添加 AMQP 依赖和安全配置模板

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Create: `src/main/resources/application-local.example.yaml`
- Test: `src/test/java/com/lzq/shortlink/ShortLinkApplicationTests.java`

- [ ] **Step 1: 先执行现有上下文测试**

运行 `mvn test -Dtest=ShortLinkApplicationTests`。预期：当前上下文可启动；加入 Starter 后必须仍通过。

- [ ] **Step 2: 添加依赖与重试配置**

在 `pom.xml` 加入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

在 `application.yaml` 的 `spring` 下加入：

```yaml
  rabbitmq:
    listener:
      simple:
        default-requeue-rejected: false
        retry:
          enabled: true
          initial-interval: 1000ms
          max-attempts: 3
          max-interval: 3000ms
          multiplier: 1
```

创建不含真实密码的本地模板：

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: shortlink
    password: 请填写本地 RabbitMQ 密码
  data:
    redis:
      host: localhost
      port: 6379

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

- [ ] **Step 3: 验证并提交**

运行 `mvn test -Dtest=ShortLinkApplicationTests`，预期零失败。使用 `git add pom.xml src/main/resources/application.yaml src/main/resources/application-local.example.yaml` 后执行提交信息 `build: 添加 RabbitMQ 基础依赖`；提交中不得包含 `application-local.yaml`。

### Task 2: 声明拓扑和消息模型

**Files:**
- Create: `src/main/java/com/lzq/shortlink/config/RabbitMqConfig.java`
- Create: `src/main/java/com/lzq/shortlink/message/VisitEvent.java`
- Create: `src/test/java/com/lzq/shortlink/config/RabbitMqConfigTest.java`

- [ ] **Step 1: 写出失败的队列死信参数测试**

```java
@Test
void shouldCreateVisitQueueWithDeadLetterConfiguration() {
    RabbitMqConfig config = new RabbitMqConfig();
    Queue queue = config.visitQueue();

    assertEquals(RabbitMqConfig.VISIT_QUEUE, queue.getName());
    assertEquals(RabbitMqConfig.DLX_EXCHANGE,
            queue.getArguments().get("x-dead-letter-exchange"));
    assertEquals(RabbitMqConfig.VISIT_DEAD_LETTER_ROUTING_KEY,
            queue.getArguments().get("x-dead-letter-routing-key"));
}
```

- [ ] **Step 2: 确认测试失败**

运行 `mvn test -Dtest=RabbitMqConfigTest`。预期：`RabbitMqConfig` 尚不存在导致编译失败。

- [ ] **Step 3: 最小实现 RabbitMQ 资源**

```java
public static final String EVENT_EXCHANGE = "short-link.events";
public static final String VISIT_ROUTING_KEY = "link.visit";
public static final String VISIT_QUEUE = "short-link.visit.queue";
public static final String DLX_EXCHANGE = "short-link.dlx";
public static final String VISIT_DEAD_LETTER_ROUTING_KEY = "link.visit.dead";
public static final String VISIT_DEAD_LETTER_QUEUE = "short-link.visit.dlq";

@Bean
Queue visitQueue() {
    return QueueBuilder.durable(VISIT_QUEUE)
            .deadLetterExchange(DLX_EXCHANGE)
            .deadLetterRoutingKey(VISIT_DEAD_LETTER_ROUTING_KEY)
            .build();
}
```

补全两个 `DirectExchange`、主/死信队列的 `Binding`，并声明：

```java
@Bean
MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
}
```

访问消息是：

```java
public record VisitEvent(
        String eventId,
        Long shortLinkId,
        String shortCode,
        LocalDateTime visitedAt
) {
}
```

- [ ] **Step 4: 验证并提交**

运行 `mvn test -Dtest=RabbitMqConfigTest`，预期零失败；提交 `RabbitMqConfig.java`、`VisitEvent.java`、`RabbitMqConfigTest.java`，提交信息为 `feat: 声明访问事件消息拓扑`。

### Task 3: 新建事件去重和每日 PV 持久化层

**Files:**
- Create: `docs/sql/2026-09-16-add-daily-visit-stat.sql`
- Create: `src/main/java/com/lzq/shortlink/entity/ShortLinkVisitEvent.java`
- Create: `src/main/java/com/lzq/shortlink/entity/ShortLinkDailyStat.java`
- Create: `src/main/java/com/lzq/shortlink/mapper/ShortLinkVisitEventMapper.java`
- Create: `src/main/java/com/lzq/shortlink/mapper/ShortLinkDailyStatMapper.java`
- Create: `src/test/java/com/lzq/shortlink/mapper/ShortLinkDailyStatMapperTest.java`

- [ ] **Step 1: 写出失败的每日累计测试**

```java
@Test
@Transactional
void shouldAccumulatePvForSameShortLinkAndDate() {
    Long shortLinkId = 10001L;
    LocalDate statDate = LocalDate.of(2026, 9, 16);

    shortLinkDailyStatMapper.incrementPv(shortLinkId, statDate);
    shortLinkDailyStatMapper.incrementPv(shortLinkId, statDate);

    ShortLinkDailyStat stat = shortLinkDailyStatMapper.selectOne(
            new LambdaQueryWrapper<ShortLinkDailyStat>()
                    .eq(ShortLinkDailyStat::getShortLinkId, shortLinkId)
                    .eq(ShortLinkDailyStat::getStatDate, statDate)
    );
    assertEquals(2L, stat.getPv());
}
```

- [ ] **Step 2: 创建迁移 SQL 并执行**

```sql
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
    short_link_id BIGINT UNSIGNED NOT NULL,
    stat_date DATE NOT NULL,
    pv BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_short_link_id_stat_date (short_link_id, stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '短链接每日访问统计表';
```

- [ ] **Step 3: 实现实体与 Mapper**

事件实体使用 `@TableName("short_link_visit_event")` 和输入型 `eventId` 主键。事件 Mapper 使用：

```java
@Insert("""
        INSERT IGNORE INTO short_link_visit_event
        (event_id, short_link_id, visited_at)
        VALUES (#{eventId}, #{shortLinkId}, #{visitedAt})
        """)
int insertIgnore(ShortLinkVisitEvent visitEvent);
```

每日统计 Mapper 使用：

```java
@Insert("""
        INSERT INTO short_link_daily_stat (short_link_id, stat_date, pv)
        VALUES (#{shortLinkId}, #{statDate}, 1)
        ON DUPLICATE KEY UPDATE pv = pv + 1, updated_at = NOW()
        """)
int incrementPv(@Param("shortLinkId") Long shortLinkId,
                @Param("statDate") LocalDate statDate);
```

- [ ] **Step 4: 运行、验证并提交**

先在 MySQL 执行迁移，再运行 `mvn test -Dtest=ShortLinkDailyStatMapperTest`。预期：同链接同日期的 PV 为 2。提交迁移、实体、Mapper 和测试，提交信息为 `feat: 添加幂等访问事件与每日 PV 存储`。

### Task 4: 发布访问事件且隔离 RabbitMQ 故障

**Files:**
- Create: `src/main/java/com/lzq/shortlink/message/VisitEventPublisher.java`
- Modify: `src/main/java/com/lzq/shortlink/service/ShortLinkService.java`
- Modify: `src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`
- Modify: `src/main/java/com/lzq/shortlink/controller/RedirectController.java`
- Modify: `src/test/java/com/lzq/shortlink/service/ShortLinkServiceTest.java`
- Create: `src/test/java/com/lzq/shortlink/message/VisitEventPublisherTest.java`

- [ ] **Step 1: 写出发布成功和故障隔离测试**

```java
@Test
void shouldPublishEventWithShortLinkIdentity() {
    publisher.publish(shortLink);

    verify(rabbitTemplate).convertAndSend(
            eq(RabbitMqConfig.EVENT_EXCHANGE),
            eq(RabbitMqConfig.VISIT_ROUTING_KEY),
            argThat(event -> event.shortLinkId().equals(1L)
                    && event.shortCode().equals("abc12345"))
    );
}

@Test
void shouldIgnorePublishFailure() {
    doThrow(new AmqpException("连接失败")).when(rabbitTemplate)
            .convertAndSend(anyString(), anyString(), any(VisitEvent.class));

    assertDoesNotThrow(() -> publisher.publish(shortLink));
}
```

- [ ] **Step 2: 运行失败测试**

运行 `mvn test -Dtest=VisitEventPublisherTest`。预期：`VisitEventPublisher` 尚不存在导致编译失败。

- [ ] **Step 3: 实现发布器并修改访问记录签名**

```java
public void publish(ShortLink shortLink) {
    VisitEvent event = new VisitEvent(
            UUID.randomUUID().toString().replace("-", ""),
            shortLink.getId(), shortLink.getShortCode(), LocalDateTime.now()
    );
    try {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EVENT_EXCHANGE,
                RabbitMqConfig.VISIT_ROUTING_KEY, event);
    } catch (AmqpException exception) {
        log.warn("访问事件发布失败，跳转不受影响，shortCode={}",
                shortLink.getShortCode(), exception);
    }
}
```

将接口由 `void recordVisit(String shortCode)` 改为 `void recordVisit(ShortLink shortLink)`。服务实现以 `shortLink.getShortCode()` 维持 Redis 递增，再调用发布器；控制器改为 `shortLinkService.recordVisit(shortLink)`。测试中的 `FixedShortCodeService` 构造函数同步传入 mock `VisitEventPublisher`。

- [ ] **Step 4: 验证并提交**

运行 `mvn test -Dtest=VisitEventPublisherTest,ShortLinkServiceTest`。预期：发布异常不抛出，Redis 逻辑仍可运行。提交发布器、服务和控制器改动，提交信息为 `feat: 异步发布短链接访问事件`。

### Task 5: 事务消费、幂等处理和死信验收

**Files:**
- Create: `src/main/java/com/lzq/shortlink/message/VisitEventConsumer.java`
- Create: `src/test/java/com/lzq/shortlink/message/VisitEventConsumerTest.java`
- Modify: `docs/短链接系统开发路线.md`

- [ ] **Step 1: 写出消费者幂等测试**

```java
@Test
void shouldIncrementDailyPvOnlyForNewEvent() {
    when(visitEventMapper.insertIgnore(any())).thenReturn(1);

    consumer.consume(event);

    verify(dailyStatMapper).incrementPv(1L, LocalDate.of(2026, 9, 16));
}

@Test
void shouldSkipDailyPvWhenEventAlreadyExists() {
    when(visitEventMapper.insertIgnore(any())).thenReturn(0);

    consumer.consume(event);

    verifyNoInteractions(dailyStatMapper);
}
```

- [ ] **Step 2: 运行失败测试**

运行 `mvn test -Dtest=VisitEventConsumerTest`。预期：`VisitEventConsumer` 尚不存在导致编译失败。

- [ ] **Step 3: 事务消费实现**

```java
@RabbitListener(queues = RabbitMqConfig.VISIT_QUEUE)
@Transactional
public void consume(VisitEvent event) {
    ShortLinkVisitEvent visitEvent = new ShortLinkVisitEvent();
    visitEvent.setEventId(event.eventId());
    visitEvent.setShortLinkId(event.shortLinkId());
    visitEvent.setVisitedAt(event.visitedAt());

    if (visitEventMapper.insertIgnore(visitEvent) == 0) {
        log.info("重复访问事件已忽略，eventId={}", event.eventId());
        return;
    }
    dailyStatMapper.incrementPv(event.shortLinkId(), event.visitedAt().toLocalDate());
}
```

不捕获数据库异常，使 Spring AMQP 重试三次并最终路由到 `short-link.visit.dlq`。在开发路线文档写明当前采用“至少一次投递 + 消费端去重”，并非 Outbox 或跨库事务。

- [ ] **Step 4: 全量验证和人工验收**

运行 `mvn test` 和 `git diff --check`。在 MySQL 执行迁移后，创建短链接并访问两次，然后执行：

```sql
SELECT short_link_id, stat_date, pv
FROM short_link_daily_stat
ORDER BY id DESC
LIMIT 5;
```

预期：对应链接当天 `pv=2`，RabbitMQ 管理台存在 `short-link.visit.queue` 与 `short-link.visit.dlq`，正常消息处理后主队列消息数为 0。

- [ ] **Step 5: 提交最终实现**

提交消费者、消费者测试和开发路线文档，提交信息为 `feat: 消费访问事件并统计每日 PV`。

## 自检

- 设计要求均有对应任务：拓扑与 DLQ（Task 2）、不阻断跳转的发布（Task 4）、去重与每日统计事务（Task 3/5）、重试与死信（Task 1/5）、Redis 累计计数保留（Task 4）、本地验证（Task 5）。
- 已完成占位语扫描，计划不含待定项或后续补充式表述。
- 类型保持一致：`VisitEvent.shortLinkId`、两个 Mapper 的 `Long shortLinkId`、服务的 `recordVisit(ShortLink)` 和控制器传入值一致；`visitedAt` 为 `LocalDateTime`，统计分组时明确转换为 `LocalDate`。
