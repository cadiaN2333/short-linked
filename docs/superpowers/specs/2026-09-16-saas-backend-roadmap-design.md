# 单体 SaaS 短链接后端总体设计

## 1. 项目定位

本项目定位为模块化单体 SaaS 短链接后端。项目不实现前端页面，但必须提供可被 Vue、React、Apifox 等客户端直接联调的稳定 REST API。

第一版不拆分微服务、不接入支付系统、不实现复杂分布式事务。项目通过用户、个人工作空间、数据隔离、短链接管理、访问分析、配额、限流和部署能力体现 SaaS 特征。

第一版采用个人工作空间模式：用户注册后自动创建个人工作空间，所有短链接均归属于一个工作空间。数据表和接口保留工作空间边界，为后续增加团队成员与协作权限提供演进空间。

## 2. 架构方案

应用使用一个 Spring Boot 进程，内部按业务职责划分模块：

- Auth：注册、登录、刷新令牌和退出登录。
- Workspace：个人工作空间和租户边界。
- Link：短链接创建、修改、查询、状态和删除。
- Redirect：公开短码解析和 302 跳转。
- Analytics：访问事件消费、聚合和趋势查询。
- Common：统一异常、响应格式、审计、限流和安全工具。

基础设施职责：

- MySQL 保存用户、工作空间、短链接、刷新令牌和聚合统计，是最终数据来源。
- Redis 保存跳转缓存、访问增量、限流计数和必要的临时状态。
- RabbitMQ 异步传递访问事件，使统计处理不阻塞跳转主链路。

公开跳转链路：

```text
GET /{shortCode}
  -> Redis 查询
  -> 未命中或故障时查询 MySQL
  -> 校验链接状态与过期时间
  -> Redis 增加累计访问增量
  -> RabbitMQ 发布访问事件
  -> 返回 302
```

统计处理链路：

```text
RabbitMQ 访问事件
  -> 事件 ID 幂等写入
  -> 按短链接和日期原子累加 PV
  -> 分析接口按时间范围查询
  -> 补齐没有访问记录的日期
```

## 3. 技术栈

### 3.1 核心技术

- Java 17 或更高版本
- Spring Boot 4
- Spring Web MVC
- Spring Security
- JWT
- Jakarta Validation
- MyBatis-Plus
- MySQL 8
- Redis
- RabbitMQ
- Maven
- Lombok

### 3.2 工程能力

- Flyway：数据库版本迁移
- Springdoc OpenAPI：Swagger 接口文档
- Spring Boot Actuator：健康检查
- JUnit 5、Mockito、MockMvc：自动化测试
- Testcontainers：MySQL、Redis、RabbitMQ 集成测试
- Docker Compose：本地依赖和部署编排
- Apifox：接口联调与环境管理
- k6 或 JMeter：跳转接口压测

## 4. 功能范围

### 4.1 用户与鉴权

接口：

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/users/me
```

要求：

- 密码使用 BCrypt 存储。
- Access Token 使用较短有效期。
- Refresh Token 支持续期和退出失效。
- 注册、登录、公开跳转之外的接口默认需要鉴权。
- 日志不得输出密码、JWT、刷新令牌和管理凭证。

### 4.2 个人工作空间

用户注册后自动创建一个个人工作空间。第一版只有拥有者，不实现成员邀请，但所有管理数据必须包含并校验 `workspace_id`。

接口：

```text
GET /api/v1/workspaces
GET /api/v1/workspaces/{workspaceId}
```

### 4.3 短链接管理

接口：

```text
POST   /api/v1/workspaces/{workspaceId}/links
GET    /api/v1/workspaces/{workspaceId}/links
GET    /api/v1/workspaces/{workspaceId}/links/{linkId}
PUT    /api/v1/workspaces/{workspaceId}/links/{linkId}
PATCH  /api/v1/workspaces/{workspaceId}/links/{linkId}/status
DELETE /api/v1/workspaces/{workspaceId}/links/{linkId}
GET    /{shortCode}
```

功能：

- 随机短码与自定义短码。
- 原始 URL 最大长度 2048。
- 可设置过期时间。
- 支持启用和禁用。
- 支持软删除。
- 支持分页、状态筛选和关键词查询。
- 短码全局唯一。
- 禁止短码使用 `api`、`actuator`、`swagger-ui` 等保留路径。

### 4.4 访问分析

管理接口：

```text
GET /api/v1/workspaces/{workspaceId}/links/{linkId}/analytics
```

查询参数：

```text
from=2026-09-10
to=2026-09-16
granularity=day
```

第一版规则：

- 未传日期时默认最近 7 个自然日，包含当天。
- 查询范围最大为 90 个自然日。
- `from` 不得晚于 `to`。
- 第一版只允许 `day` 粒度。
- 没有统计数据的日期返回 `pv=0`。
- 趋势按日期升序返回。
- 只能查询当前工作空间拥有的短链接。

响应包含：

- 查询范围内 PV。
- 已落库累计 PV。
- 最近访问时间。
- 按日期排列的趋势数据。

后续可以增加 UV、来源域名、设备、浏览器、地区以及小时、月粒度。

### 4.5 配额与限流

第一版不收费，但仍提供可配置的基础配额：

- 每个工作空间允许创建的最大链接数。
- 单位时间内创建链接的次数限制。
- 登录失败次数限制。
- 跳转接口按 IP 限流。
- 超出配额或限流时返回 HTTP 429 和稳定错误码。

### 4.6 运维能力

- 提供 `/actuator/health` 和 `/actuator/info`。
- RabbitMQ 配置主队列、消费重试和 DLQ。
- Redis 故障时跳转降级查询 MySQL。
- RabbitMQ 故障不阻止 302 跳转。
- 日志携带 `requestId`，错误响应也返回该标识。
- 提供 Docker Compose 启动 MySQL、Redis 和 RabbitMQ。

## 5. 数据模型

核心表：

```text
user
workspace
short_link
refresh_token
short_link_visit_event
short_link_daily_stat
```

`short_link` 至少包含：

```text
id
workspace_id
short_code
original_url
status
expire_at
visit_count
last_visited_at
created_at
updated_at
deleted
```

关键约束：

- `short_link.short_code` 全局唯一。
- `short_link_daily_stat(short_link_id, stat_date)` 唯一。
- `short_link_visit_event.event_id` 唯一。
- 管理查询必须同时校验资源 ID 和工作空间 ID。
- 禁止只凭可枚举的 `linkId` 查询或修改资源。

## 6. API 联调规范

- 管理接口统一使用 `/api/v1` 前缀。
- JSON 字段统一使用 camelCase。
- 时间使用 ISO 8601 格式。
- 分页参数统一为 `page`、`size`，响应提供总记录数。
- CORS 允许的来源必须通过配置注入，生产环境不得使用任意来源。
- 使用 OpenAPI 输出接口文档。
- 提供 Apifox 集合、开发环境变量和示例请求。

统一错误响应：

```json
{
  "code": "LINK_NOT_FOUND",
  "message": "短链接不存在",
  "requestId": "f34a...",
  "timestamp": "2026-09-16T20:30:00+08:00"
}
```

HTTP 状态约定：

- 400：参数或请求格式错误。
- 401：未登录或令牌失效。
- 403：没有工作空间权限。
- 404：资源不存在。
- 409：短码等唯一资源冲突。
- 429：触发限流或配额。
- 500：系统内部错误。

## 7. 一致性与功能边界

- 302 跳转是核心链路，缓存和统计组件故障不得阻止合法链接跳转。
- MySQL 是最终数据来源，Redis 数据允许丢失并能够重新构建。
- 每日统计采用最终一致性，允许短暂延迟。
- RabbitMQ 使用至少一次投递，消费者必须基于事件 ID 幂等。
- 当前分析数据不用于财务级计费。
- 当前 `manageToken` 仅作为兼容能力；用户体系完成后，管理权限由 JWT 和工作空间归属决定。
- 第一版不实现支付、微服务、服务注册发现、分布式事务和复杂推荐算法。
- 第一版不实现自定义域名的 DNS 和证书自动化。
- 访问事件去重数据需要定期清理，默认保留 90 天；每日聚合统计可长期保留。

## 8. 安全要求

- 密码使用 BCrypt，严禁明文保存。
- 所有管理操作校验当前用户与 `workspace_id` 的归属关系。
- 禁止日志输出密码、令牌、管理凭证和完整敏感请求体。
- URL 只允许 HTTP 和 HTTPS，最大长度 2048。
- 已禁用、已过期或已删除链接不得通过 Redis 继续跳转。
- 登录、创建和公开跳转接口配置独立限流规则。
- 自定义短码不得覆盖系统保留路径。
- Redis Key 使用稳定前缀，避免不同业务互相覆盖。

## 9. 测试与验收

### 9.1 单元测试

- 短码生成和唯一冲突重试。
- URL、过期时间和分析日期范围校验。
- JWT 创建、解析、过期和篡改场景。
- 工作空间归属判断。
- 最近 7 天趋势补零和升序排列。
- 配额和限流判断。
- RabbitMQ 消费幂等。
- 链接过期、禁用和删除状态判断。

### 9.2 集成测试

- MySQL Mapper 和 Flyway 迁移。
- Redis 缓存命中、缓存回填和故障降级。
- RabbitMQ 发布、消费、重试和 DLQ。
- Spring Security 和 JWT 鉴权。
- 用户之间、工作空间之间的数据隔离。
- 管理接口的分页和筛选。

### 9.3 API 验收

1. 用户能够注册、登录、刷新和退出。
2. 注册后自动创建个人工作空间。
3. 用户只能查询和修改自己的工作空间数据。
4. 能够创建、分页查询、修改、禁用和删除链接。
5. 禁用、过期和删除的链接不能跳转。
6. Redis 停止后合法链接仍能通过 MySQL 跳转。
7. RabbitMQ 停止后合法链接仍能跳转。
8. 重复事件不会重复增加每日 PV。
9. 默认分析接口返回最近 7 天并补齐零值日期。
10. 查询范围超过 90 天时返回 400。
11. 用户 A 无法访问用户 B 的链接或统计。
12. Swagger 和 Apifox 能够完成完整接口联调。

### 9.4 性能验收

- 对公开跳转接口进行并发压测。
- 比较 Redis 命中与 MySQL 查询的响应时间。
- 记录吞吐量、P95 延迟和错误率。
- 验证 RabbitMQ 出现积压后能够恢复消费。
- 压测数据必须来自实际运行结果，不得使用虚构指标。

## 10. 实施顺序

1. 整理、验证并提交当前 RabbitMQ 访问统计能力。
2. 实现可扩展的最近 7 天分析接口。
3. 使用 Flyway 接管数据库迁移。
4. 实现用户注册、登录、JWT 和刷新令牌。
5. 实现个人工作空间和租户隔离。
6. 完成短链接 CRUD、状态、软删除、分页和筛选。
7. 将统计查询迁移到 JWT 与工作空间权限。
8. 增加配额、限流、保留路径和安全处理。
9. 增加 OpenAPI、CORS、Actuator 和统一 requestId。
10. 完成 Docker Compose、Testcontainers、Apifox 集合和压测报告。

每个阶段必须保持应用可运行、测试可复现，并在进入下一阶段前完成对应验收。
