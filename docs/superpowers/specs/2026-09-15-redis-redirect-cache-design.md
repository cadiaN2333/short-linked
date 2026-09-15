# Redis 跳转缓存设计

## 目标

在不改变短链接创建接口行为的前提下，为 `GET /{shortCode}` 增加 Redis 缓存。访问有效短链接时优先读取 Redis；缓存未命中才查询 MySQL，并将查询结果回填缓存。

## 范围

本阶段只缓存跳转查询，不实现缓存预热、空值缓存、访问统计、删除短链接或修改目标地址。

## 缓存模型

- 键：`short-link:{shortCode}`。
- 值：原始长链接字符串。
- 永久有效短链接：缓存不设置 TTL。
- 有过期时间的短链接：缓存 TTL 等于 `expireAt` 与当前时间的剩余时长。
- 已过期或不存在的短链接：不写入缓存，仍返回 404。

## 请求流程

```text
GET /{shortCode}
    ↓
查询 Redis
    ├─ 命中：读取原始链接，返回 302
    └─ 未命中：查询 MySQL
                  ├─ 不存在或已过期：返回 404
                  └─ 有效：写入 Redis，返回 302
```

## 组件职责

- `ShortLinkService`：负责 Redis 查询、MySQL 回源、过期判断和缓存回填，向 Controller 返回有效短链接。
- `RedirectController`：只负责将有效链接转换成 302 响应，或将不存在结果转换成 404。
- `application-local.yaml`：保存本机 Redis 地址和端口，不提交 Git。
- `application-local.yaml.example`：提供不含凭据的 Redis 配置示例，可提交 Git。

## 错误与降级

若 Redis 连接不可用，本阶段让应用通过明确错误暴露配置问题，不静默降级到 MySQL。Redis 已在本机 Docker 中运行后，再补 Redis 不可用降级逻辑，避免首次引入缓存时同时增加多个行为分支。

## 验收标准

- Redis 容器运行在本机 `localhost:6379`。
- 首次访问有效短链接时，从 MySQL 查询并写入键 `short-link:{shortCode}`。
- 再次访问同一短链接时，从 Redis 命中并返回 302。
- 有过期时间的短链接缓存不会晚于链接本身过期。
- 不存在或已过期短码仍返回统一的 404 JSON 响应。
