# 多租户短链接管理与访问分析平台

一个基于 Java 17 和 Spring Boot 的模块化单体 SaaS 短链接后端项目。项目面向个人或团队工作空间，提供短链接创建、302 跳转、生命周期管理、访问事件异步处理和每日访问趋势查询。

## 项目亮点

- 使用 JWT 和 Refresh Token 完成登录认证，按工作空间成员关系进行多租户权限校验。
- 支持 Base62 随机短码和自定义短码，使用数据库唯一索引处理并发冲突。
- Redis 缓存短链接目标地址和状态，并通过负缓存、脏缓存校验和 MySQL 回源处理常见缓存故障。
- RabbitMQ 异步发布访问事件，消费者按事件 ID 幂等，Redis 增量计数定时同步到 MySQL 每日统计表。
- 使用 Redis Lua 原子领取、processing Key、失败回补和超时恢复降低定时同步丢计数风险。
- 使用 Flyway 管理数据库迁移，使用 JUnit、Mockito 和 Spring Boot Test 验证核心业务与故障分支。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 语言与框架 | Java 17、Spring Boot、Spring MVC、Spring Security |
| 数据访问 | MyBatis-Plus、MySQL、Flyway |
| 缓存与消息 | Redis、RabbitMQ |
| 认证 | JWT、Refresh Token、BCrypt |
| 测试与构建 | JUnit、Mockito、Spring Boot Test、Maven |

## 模块结构

```text
com.lzq.shortlink
├── auth          用户注册、登录、刷新令牌和 JWT 配置
├── controller    认证、工作空间、短链接和分析接口
├── service       短链接、工作空间和统计业务
├── mapper        MyBatis-Plus Mapper 与关键 SQL
├── message       RabbitMQ 访问事件发布与消费
├── scheduler     Redis 访问计数定时同步
├── workspace     工作空间成员关系和权限校验
├── entity/dto    数据库实体、请求对象和响应对象
└── exception     统一异常处理和业务异常
```

## 核心请求链路

```text
用户请求
   │
   ├─ 管理端：JWT → 工作空间成员校验 → 短链接管理/分析
   │
   └─ 公开跳转：Redis 目标地址与状态
                 ├─ 命中且 ACTIVE → 302
                 └─ 未命中/故障 → MySQL → 回填缓存 → 302
                                      │
                                      ├─ Redis 访问增量
                                      └─ RabbitMQ 访问事件 → 幂等消费 → 每日 PV
```

## 本地启动

### 1. 准备依赖

需要本地启动：

- MySQL 8，创建数据库 `short_link`
- Redis，默认 `localhost:6379`
- RabbitMQ，默认 `localhost:5672`
- Java 17 或更高版本

数据库结构由 Flyway 在应用启动时自动迁移，迁移脚本位于 `src/main/resources/db/migration`。

### 2. 配置本地数据库

复制示例配置并填写本机账号密码：

```powershell
Copy-Item src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
```

`application-local.yaml` 已加入 `.gitignore`，不会被提交。启动前还需要在当前 PowerShell 会话设置 JWT 密钥：

```powershell
$env:SHORT_LINK_JWT_SECRET = "请替换为至少32字节的随机字符串"
$env:SHORT_LINK_PUBLIC_BASE_URL = "http://localhost:8080"
```

### 3. 编译与测试

```powershell
./mvnw.cmd -DskipTests compile
./mvnw.cmd test
```

需要连接 MySQL、Redis 或 RabbitMQ 的集成测试，请先启动对应服务；只做快速编译时可使用 `-DskipTests`。

## 接口示例

### 注册并登录

```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "demo@example.com",
  "password": "Demo@123456"
}
```

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "demo@example.com",
  "password": "Demo@123456"
}
```

登录返回的 `accessToken` 通过请求头传递：

```http
Authorization: Bearer {accessToken}
```

### 工作空间短链接

```http
POST /api/v1/workspaces/{workspaceId}/links
GET  /api/v1/workspaces/{workspaceId}/links?page=1&pageSize=20
GET  /api/v1/workspaces/{workspaceId}/links/{linkId}
PATCH /api/v1/workspaces/{workspaceId}/links/{linkId}/status
DELETE /api/v1/workspaces/{workspaceId}/links/{linkId}
```

公开跳转接口：

```http
GET /{shortCode}
```

访问分析接口：

```http
GET /api/v1/workspaces/{workspaceId}/links/{linkId}/analytics?granularity=day
```

## 设计取舍

- 统计链路采用“消息至少投递一次 + 消费端幂等”，当前不宣称 exactly-once。
- RabbitMQ 或 Redis 故障不阻断有效短链接跳转，统计数据允许短暂延迟。
- 当前采用模块化单体，暂不引入微服务、Outbox、DLQ 自动重放和监控平台，避免脱离项目规模堆砌组件。
- `keyword` 的前后模糊查询在数据量较大时可能无法使用普通 B-Tree 索引，后续可评估全文索引或搜索服务。

## 工程目录

- `src/main/java`：业务代码、接口、权限、缓存、消息和定时任务
- `src/main/resources/db/migration`：Flyway 数据库迁移脚本
- `src/test/java`：单元测试、集成测试和 SQL 契约测试
