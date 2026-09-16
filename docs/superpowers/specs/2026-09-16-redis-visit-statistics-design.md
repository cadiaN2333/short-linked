# Redis 访问统计设计

## 目标

为有效短链接跳转记录总访问次数和最近访问时间，同时避免每次跳转都直接更新 MySQL。访问请求使用 Redis 原子计数，定时任务再批量将增量写入 MySQL。

## 范围

本阶段只实现总访问次数和最近访问时间，不记录 IP、浏览器、来源页面、逐条访问日志或按天统计。统计数据采用最终一致模型。

## 数据模型

`short_link` 表新增字段：

- `visit_count BIGINT NOT NULL DEFAULT 0`：已落库的累计访问次数。
- `last_visited_at DATETIME NULL`：最近一次成功落库的访问时间。

Redis 使用增量计数键：

```text
short-link:visit:{shortCode}
```

键值为尚未落库的访问增量。

## 访问流程

```text
GET /{shortCode}
    ↓
查询有效短链接
    ├─ 不存在或过期：返回 404，不统计
    └─ 有效：Redis INCR 访问计数键
                ↓
              返回 302
```

Redis 计数失败时仅记录警告，跳转仍返回 302，不让统计功能影响核心跳转链路。

## 定时同步流程

每分钟由 `VisitStatSyncScheduler` 执行：

```text
SCAN short-link:visit:*
    ↓
对每个键原子 GETDEL，取得本批次增量
    ↓
MySQL：visit_count = visit_count + 增量
       last_visited_at = 当前时间
```

`GETDEL` 使“取出旧计数”和“删除旧键”成为一个原子动作。同步期间新到的 `INCR` 会写入新的计数键，留给下一轮同步处理。

## 组件职责

- `RedirectController`：仅在确认短链接有效后调用统计记录方法。
- `ShortLinkService`：提供访问计数记录能力，并保持 Redis 失败不影响跳转。
- `VisitStatSyncScheduler`：扫描 Redis 计数键、原子取出增量并调用 Mapper 批量累加。
- `ShortLinkMapper`：执行访问次数累加和最近访问时间更新。

## 一致性与故障边界

- 页面跳转优先级高于统计准确性；Redis 或统计落库失败不会阻断 302。
- MySQL 只保存已同步的数据，因此统计查询最多存在约一分钟延迟。
- 如果进程在 `GETDEL` 成功后、MySQL 累加前崩溃，该批计数可能丢失。
- Redis 未开启可靠持久化或 Redis 故障时，尚未同步的计数也可能丢失。
- 下一阶段可用 Redis Stream、消费者组与数据库幂等事件表缩小或消除此类丢失和重复计数风险。

## 验收标准

- 有效短链接跳转一次后，对应 Redis 计数键增加 1。
- 不存在或过期短码不创建或增加访问计数键。
- 定时任务执行后，Redis 中已同步的计数键被移除，MySQL 的 `visit_count` 累加对应值。
- `last_visited_at` 在同步后更新。
- Redis 不可用时，跳转仍返回 302，控制台有统计失败警告日志。
