# RabbitMQ 访问事件异步化设计

## 目标

在不拆分微服务的前提下，为短链接单体应用引入 RabbitMQ。RabbitMQ 只负责异步访问事件与按天统计，不参与短链接跳转的主链路。

现有 Redis 访问计数和 MySQL 最终累计访问次数继续保留。RabbitMQ 新增的价值是：让每日 PV 统计、失败重试和死信处理从同步跳转请求中剥离出来。

## 范围与非目标

本阶段包含：

- RabbitMQ Docker 容器的本地连接配置。
- 有效跳转后发布访问事件。
- 持久化交换机、正常队列、重试后的死信队列。
- 消费访问事件并更新按天 PV 统计。
- 通过事件唯一标识避免重复消费导致重复计数。

本阶段不包含：

- 微服务拆分、Kafka、支付与订单。
- 用户、JWT 和租户模块；这些在下一阶段接入。
- IP、设备、地理位置等隐私敏感的访问明细。
- 发布端 Outbox 事务表。发布端临时不可用时会记录告警，但不影响 302 跳转和现有总 PV 计数。

## 架构与数据流

```text
有效短链接访问
        │
        ▼
RedirectController ──► ShortLinkService
        │                     │
        │                     ├──► Redis：访问次数加一（现有逻辑）
        │                     └──► RabbitMQ：发布 VisitEvent
        ▼
302 重定向立即返回

RabbitMQ 访问队列 ──► VisitEventConsumer
                                  │
                                  ├──► short_link_visit_event：事件去重
                                  └──► short_link_daily_stat：按天 PV 累加
```

跳转接口不会等待消费者完成。RabbitMQ 故障时，跳转和 Redis 总访问次数仍正常工作；受影响的仅是新增的每日统计明细。

## RabbitMQ 拓扑

| 组件 | 名称 | 作用 |
|---|---|---|
| 直连交换机 | `short-link.events` | 路由正常业务事件 |
| 路由键 | `link.visit` | 标识短链接访问事件 |
| 持久化队列 | `short-link.visit.queue` | 供访问统计消费者消费 |
| 死信交换机 | `short-link.dlx` | 接收重试耗尽的消息 |
| 死信队列 | `short-link.visit.dlq` | 供人工排查失败消息 |

消费者出现异常时，Spring AMQP 自动重试 3 次；仍失败则拒绝消息且不重新入队，由正常队列的死信配置将它路由到 `short-link.visit.dlq`。

## 事件模型

`VisitEvent` 仅包含统计所需的最小字段：

```text
eventId       32 位随机标识
shortLinkId   短链接主键
shortCode     短码，便于日志排查
visitedAt     访问时间
```

不携带原始链接、管理凭证或用户隐私数据。

## 数据模型

新增两张表：

```text
short_link_visit_event
- event_id          主键，消息去重依据
- short_link_id     短链接主键
- visited_at        访问时间
- created_at        入库时间

short_link_daily_stat
- id                主键
- short_link_id     短链接主键
- stat_date         统计日期
- pv                当日访问次数
- created_at
- updated_at
- 唯一索引：(short_link_id, stat_date)
```

消费者在一个数据库事务内先插入 `short_link_visit_event`。如果 `event_id` 已存在，说明 RabbitMQ 正在重投同一消息，消费者直接确认消息且不再增加 PV；只有首次插入成功时才对 `short_link_daily_stat` 做递增更新。

## 组件职责

- `RabbitMqConfig`：声明交换机、队列、绑定和死信配置。
- `VisitEventPublisher`：在有效跳转后发布事件，不记录敏感字段。
- `VisitEventConsumer`：消费事件、执行去重和每日 PV 聚合。
- `VisitEventMapper`：写入去重事件。
- `ShortLinkDailyStatMapper`：按日期原子递增 PV。
- `VisitStatSyncScheduler`：继续把 Redis 中的总访问增量落库到 `short_link.visit_count`，与 RabbitMQ 每日统计职责互不重复。

## 本地配置

RabbitMQ 账号和密码放在被 Git 忽略的 `application-local.yaml`：

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: shortlink
    password: 本地密码
```

公共 `application.yaml` 不出现密码。

## 失败语义

- 发布失败：记录不含敏感信息的警告日志，仍返回 302；Redis 总访问次数继续累加。
- 消费失败：自动重试 3 次，之后转入死信队列。
- 重复投递：依赖 `event_id` 主键去重，保证每日 PV 不会因重复消息增加。
- MySQL 失败：消费者事务回滚，消息不会确认，会进入重试链路。

这是“消费者至少一次投递 + 数据库幂等”的实现，不承诺发布端到数据库的端到端精确一次。后续若需要更强可靠性，再引入 Outbox 模式。

## 验收标准

- RabbitMQ 管理后台能看到正常队列与死信队列。
- 访问有效短链接后，正常队列产生并被消费者处理的消息。
- `short_link_daily_stat` 对应日期的 PV 增加。
- 人为触发消费者异常后，消息重试 3 次并进入死信队列。
- 同一个 `eventId` 被重复发送时，每日 PV 只增加一次。
- RabbitMQ 不可用时，短链接仍返回 302，Redis 总访问次数仍会写入。
