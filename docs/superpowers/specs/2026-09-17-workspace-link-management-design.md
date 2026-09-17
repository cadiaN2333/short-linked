# 工作空间短链接管理设计

## 目标

让已登录用户只能在自己所属的工作空间内创建短链接，所有新建记录强制写入 `workspace_id`，为后续 CRUD、统计和配额提供稳定租户边界。

## 业务规则

1. 注册用户拥有一个个人工作空间，成员关系为 `OWNER`。
2. 管理接口使用 JWT，JWT 的 `sub` 是当前用户 ID。
3. 当前用户不是目标工作空间成员时返回 403，不泄露工作空间是否存在以外的资源信息。
4. 创建短链接接口为 `POST /api/v1/workspaces/{workspaceId}/links`。
5. 短链接公开跳转 `GET /{shortCode}` 保持匿名，不要求工作空间参数。
6. `short_link.workspace_id` 必须非空，禁止使用历史系统工作空间作为新用户请求的隐式归属。

## 实现边界

当前切片已实现工作空间查询、成员校验、创建短链接、分页列表、单条详情、修改、启用/禁用和软删除。自定义短码、配额和工作空间统计仍后续分步迁移。旧 `/api/links` 创建入口已在后续审计修复中删除；兼容统计接口暂保，待调用方迁移后废弃。

## 技术方案

- `WorkspaceMapper` 增加按用户查询工作空间和按成员关系判断权限的方法。
- 新增 `WorkspaceAccessService`，集中处理当前用户 ID 解析与成员校验。
- `ShortLink` 增加 `workspaceId` 字段，创建 Mapper 写入 `workspace_id`。
- 新增 `WorkspaceController` 提供工作空间列表和详情接口。
- 新增 `WorkspaceLinkController` 提供工作空间范围内的创建接口。
- `GET /api/v1/workspaces/{workspaceId}/links` 提供分页列表，默认每页 20 条，最多 100 条。
- `GET /api/v1/workspaces/{workspaceId}/links/{linkId}` 提供工作空间范围内详情查询。
- `PUT /api/v1/workspaces/{workspaceId}/links/{linkId}` 修改目标地址和过期时间。
- `PATCH /api/v1/workspaces/{workspaceId}/links/{linkId}/status` 切换 `ACTIVE`/`DISABLED`。
- `DELETE /api/v1/workspaces/{workspaceId}/links/{linkId}` 执行软删除，状态改为 `DELETED`。
- 公开跳转只查询 `ACTIVE` 状态，状态变更时删除 Redis 跳转缓存。
- `GET /api/v1/workspaces/{workspaceId}/links/{linkId}/analytics` 查询最近 7 天或自定义日期范围 PV 趋势，不再依赖 `X-Manage-Token`。
- 列表支持 `status=ACTIVE|DISABLED` 和 `keyword` 筛选，关键词同时匹配短码与原始 URL，已软删除记录不参与查询。
- 创建接口支持可选 `shortCode`，长度 3~16，只允许数字、字母、下划线和连字符；`api`、`actuator`、`error`、`swagger-ui`、`v3`、`favicon.ico` 等系统路径禁止占用。
- 自定义短码只尝试一次数据库写入，唯一索引冲突返回 409 `SHORT_CODE_ALREADY_EXISTS`，不自动改成随机短码。
- 无权限抛出稳定的 `WORKSPACE_ACCESS_DENIED` 错误码。

## 测试策略

- 单元测试覆盖：成员允许、非成员拒绝、JWT subject 非法。
- WebMvc 测试覆盖：列表、详情、创建成功、非成员 403。
- 分页参数校验覆盖：页码从 1 开始，每页数量限制为 1~100。
- Mapper 集成测试覆盖：创建记录的 `workspace_id` 非空且等于路径参数。
- 运行测试时通过当前 Maven 进程注入 `SHORT_LINK_JWT_SECRET`，不修改系统环境变量。
