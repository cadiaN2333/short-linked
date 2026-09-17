# 工作空间短链接管理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现基于 JWT 和工作空间成员关系的工作空间查询与短链接创建接口。

**Architecture:** 使用一个工作空间访问服务集中解析 JWT 用户 ID 并校验成员关系；控制器只负责 HTTP 参数，短链接服务负责生成短码和持久化。公开跳转接口保持匿名，管理创建接口统一使用 `/api/v1/workspaces/{workspaceId}/links`。

**Tech Stack:** Spring Boot 4、Spring Security Resource Server JWT、MyBatis-Plus、MySQL、JUnit 5、Mockito、MockMvc。

---

### Task 1: 扩展工作空间数据访问层

**Files:**
- Modify: `src/main/java/com/lzq/shortlink/workspace/WorkspaceMapper.java`
- Modify: `src/main/java/com/lzq/shortlink/workspace/WorkspaceMemberMapper.java`
- Test: `src/test/java/com/lzq/shortlink/workspace/WorkspaceMapperContextTest.java`

- [x] **Step 1: 写失败测试**：增加工作空间访问服务的成员允许、非成员拒绝和非法 subject 测试，先运行并确认服务尚不存在。
- [x] **Step 2: 实现最小 SQL**：增加 `selectActiveByUserId` 和 `selectAccessibleById`，通过 `workspace_member` 与 `workspace` 联表完成成员边界查询。
- [x] **Step 3: 运行 Mapper 上下文测试**：确认两个 Mapper 能被 Spring 注册且 SQL 能加载。

### Task 2: 实现工作空间访问服务

**Files:**
- Create: `src/main/java/com/lzq/shortlink/workspace/WorkspaceAccessService.java`
- Create: `src/main/java/com/lzq/shortlink/workspace/WorkspaceAccessDeniedException.java`
- Modify: `src/main/java/com/lzq/shortlink/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/lzq/shortlink/workspace/WorkspaceAccessServiceTest.java`

- [x] **Step 1: 写失败测试**：覆盖 JWT `sub=42` 成员允许、非成员拒绝、`sub` 不是数字拒绝。
- [x] **Step 2: 实现访问服务**：从 `SecurityContextHolder` 读取认证名称，转换为 Long；调用工作空间 Mapper 校验；失败抛出 `WorkspaceAccessDeniedException`。
- [x] **Step 3: 增加 403 错误响应**：错误码固定为 `WORKSPACE_ACCESS_DENIED`。
- [x] **Step 4: 运行服务测试**：确认 3 个场景通过。

### Task 3: 让短链接持久化携带工作空间

**Files:**
- Modify: `src/main/java/com/lzq/shortlink/entity/ShortLink.java`
- Modify: `src/main/java/com/lzq/shortlink/service/ShortLinkService.java`
- Modify: `src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`
- Modify: `src/main/java/com/lzq/shortlink/mapper/ShortLinkMapper.java`
- Test: `src/test/java/com/lzq/shortlink/service/ShortLinkWorkspaceServiceTest.java`

- [x] **Step 1: 写失败测试**：调用带 `workspaceId` 的创建方法，捕获 Mapper 参数并断言 `workspaceId` 正确传入。
- [x] **Step 2: 扩展实体和服务签名**：增加 `workspaceId` 字段，新增 `createShortLink(Long workspaceId, String originalUrl, LocalDateTime expireAt)`。
- [x] **Step 3: 实现持久化**：短码冲突重试逻辑保持不变，只把 workspace ID 写入实体后执行一次插入。
- [x] **Step 4: 运行服务测试**：确认创建记录携带工作空间 ID。

### Task 4: 增加工作空间 HTTP 接口

**Files:**
- Create: `src/main/java/com/lzq/shortlink/workspace/WorkspaceController.java`
- Create: `src/main/java/com/lzq/shortlink/workspace/WorkspaceResponse.java`
- Create: `src/main/java/com/lzq/shortlink/controller/WorkspaceLinkController.java`
- Modify: `src/main/java/com/lzq/shortlink/config/SecurityConfig.java`
- Test: `src/test/java/com/lzq/shortlink/controller/WorkspaceControllerTest.java`

- [x] **Step 1: 写失败控制器测试**：覆盖工作空间列表、详情、创建短链接成功和非成员拒绝。
- [x] **Step 2: 实现工作空间查询接口**：提供 `GET /api/v1/workspaces` 和 `GET /api/v1/workspaces/{workspaceId}`。
- [x] **Step 3: 实现创建接口**：提供 `POST /api/v1/workspaces/{workspaceId}/links`，调用访问服务后创建短链接。
- [x] **Step 4: 配置鉴权**：工作空间接口沿用默认 JWT 鉴权规则，只有认证接口和公开跳转放行。
- [x] **Step 5: 运行测试**：控制器单测和真实 Spring/MySQL/JWT 联调测试均通过。

### Task 5: 验收与日志

**Files:**
- Modify: `docs/短链接系统开发路线.md`

- [x] **Step 1: 运行针对性测试**：注入临时 JWT 密钥，执行工作空间、认证和 Flyway 测试。
- [x] **Step 2: 编译项目**：执行 `mvn -DskipTests compile`，确认源代码编译通过。
- [x] **Step 3: 更新路线日志**：记录接口、权限边界、测试结果和未迁移的旧接口。

### Task 6: 工作空间短链接查询切片

**Files:**
- Modify: `src/main/java/com/lzq/shortlink/mapper/ShortLinkMapper.java`
- Modify: `src/main/java/com/lzq/shortlink/service/ShortLinkService.java`
- Modify: `src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`
- Modify: `src/main/java/com/lzq/shortlink/controller/WorkspaceLinkController.java`
- Create: `src/main/java/com/lzq/shortlink/service/ShortLinkPageResult.java`
- Create: `src/main/java/com/lzq/shortlink/dto/ShortLinkListItemResponse.java`
- Create: `src/main/java/com/lzq/shortlink/dto/ShortLinkPageResponse.java`
- Create: `src/main/java/com/lzq/shortlink/exception/InvalidPaginationException.java`
- Test: `src/test/java/com/lzq/shortlink/service/ShortLinkWorkspaceServiceTest.java`
- Test: `src/test/java/com/lzq/shortlink/controller/WorkspaceControllerTest.java`
- Test: `src/test/java/com/lzq/shortlink/controller/WorkspaceLinkIntegrationTest.java`

- [x] **Step 1: 写失败测试**：增加工作空间分页查询、详情查询和分页参数边界测试，并确认缺少 Mapper/Service 能力时测试先失败。
- [x] **Step 2: 增加工作空间边界 SQL**：列表、总数和详情查询均携带 `workspace_id` 条件。
- [x] **Step 3: 实现分页响应**：默认每页 20 条，最大 100 条；列表和详情响应不返回 `manageToken`。
- [x] **Step 4: 联调验证**：真实 Spring、JWT、MySQL、Flyway 上下文验证列表和详情接口。
- [x] **Step 5: 更新路线日志**：记录查询接口、分页边界和验证命令。

### Task 7: 工作空间短链接生命周期管理

**Files:**
- Create: `src/main/resources/db/migration/V4__add_short_link_status.sql`
- Modify: `src/main/java/com/lzq/shortlink/entity/ShortLink.java`
- Modify: `src/main/java/com/lzq/shortlink/mapper/ShortLinkMapper.java`
- Modify: `src/main/java/com/lzq/shortlink/service/ShortLinkService.java`
- Modify: `src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`
- Modify: `src/main/java/com/lzq/shortlink/controller/WorkspaceLinkController.java`
- Create: `src/main/java/com/lzq/shortlink/dto/UpdateShortLinkStatusRequest.java`
- Create: `src/main/java/com/lzq/shortlink/exception/InvalidShortLinkStatusException.java`
- Test: `src/test/java/com/lzq/shortlink/service/ShortLinkWorkspaceServiceTest.java`
- Test: `src/test/java/com/lzq/shortlink/controller/WorkspaceControllerTest.java`
- Test: `src/test/java/com/lzq/shortlink/controller/WorkspaceLinkIntegrationTest.java`

- [x] **Step 1: 写失败测试**：增加修改、状态切换、软删除和公开跳转禁止禁用链接测试，并确认缺少状态字段和服务能力时测试先失败。
- [x] **Step 2: 增加 V4 迁移**：为 `short_link` 增加状态字段及工作空间状态索引，默认历史数据为 `ACTIVE`。
- [x] **Step 3: 实现生命周期接口**：支持 `PUT`、`PATCH /status`、`DELETE`，所有查询携带工作空间条件。
- [x] **Step 4: 处理缓存一致性**：状态或目标地址变化后删除短链接 Redis 缓存；公开跳转只允许 `ACTIVE`。
- [x] **Step 5: 联调验证**：真实 Spring、JWT、MySQL、Flyway、RabbitMQ 上下文 6 项测试通过。
- [x] **Step 6: 更新路线日志**：记录迁移版本、状态语义和环境验证命令。

### Task 8: 工作空间统计查询迁移

**Files:**
- Create: `src/main/java/com/lzq/shortlink/controller/WorkspaceAnalyticsController.java`
- Test: `src/test/java/com/lzq/shortlink/controller/WorkspaceLinkIntegrationTest.java`

- [x] **Step 1: 写失败联调测试**：使用工作空间和短链接主键查询最近 7 天 PV，确认新路径尚未注册时返回 404。
- [x] **Step 2: 实现权限边界**：先校验工作空间成员关系，再按 `workspace_id + linkId` 获取短链接。
- [x] **Step 3: 复用趋势查询**：保留 `day` 粒度、默认最近 7 天和最大 90 天范围规则。
- [x] **Step 4: 联调验证**：真实 Spring、JWT、MySQL、Flyway、RabbitMQ 上下文 7 项测试通过。
- [x] **Step 5: 更新路线日志**：记录新的前端联调路径，旧 `X-Manage-Token` 接口暂保兼容。

### Task 9: 工作空间短链接筛选

**Files:**
- Modify: `src/main/java/com/lzq/shortlink/mapper/ShortLinkMapper.java`
- Modify: `src/main/java/com/lzq/shortlink/service/ShortLinkService.java`
- Modify: `src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`
- Modify: `src/main/java/com/lzq/shortlink/controller/WorkspaceLinkController.java`
- Create: `src/main/java/com/lzq/shortlink/exception/InvalidShortLinkFilterException.java`
- Test: `src/test/java/com/lzq/shortlink/service/ShortLinkWorkspaceServiceTest.java`
- Test: `src/test/java/com/lzq/shortlink/controller/WorkspaceControllerTest.java`
- Test: `src/test/java/com/lzq/shortlink/controller/WorkspaceLinkIntegrationTest.java`

- [x] **Step 1: 写失败测试**：增加状态与关键词筛选的服务、控制器和真实联调测试，确认新 Mapper 方法尚不存在时测试先失败。
- [x] **Step 2: 增加动态 SQL**：状态只允许 `ACTIVE`/`DISABLED`，关键词匹配 `short_code` 或 `original_url`，始终排除 `DELETED`。
- [x] **Step 3: 增加参数边界**：状态大小写规范化，关键词去除首尾空格并限制 100 个字符。
- [x] **Step 4: 更新接口**：列表增加 `status`、`keyword` 查询参数，不改变无筛选请求的兼容调用路径。
- [ ] **Step 5: 联调验证**：由开发机运行 `WorkspaceLinkIntegrationTest`，确认 MySQL 动态 SQL 在本地数据库执行通过。
- [x] **Step 6: 更新路线日志**：记录筛选规则和待用户运行的联调命令。

### Task 10: 自定义短码与保留路径

**Files:**
- Modify: `src/main/java/com/lzq/shortlink/dto/CreateShortLinkRequest.java`
- Modify: `src/main/java/com/lzq/shortlink/service/ShortLinkService.java`
- Modify: `src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`
- Modify: `src/main/java/com/lzq/shortlink/controller/WorkspaceLinkController.java`
- Modify: `src/main/java/com/lzq/shortlink/exception/GlobalExceptionHandler.java`
- Create: `src/main/java/com/lzq/shortlink/exception/ReservedShortCodeException.java`
- Create: `src/main/java/com/lzq/shortlink/exception/ShortCodeAlreadyExistsException.java`
- Test: `src/test/java/com/lzq/shortlink/service/ShortLinkWorkspaceServiceTest.java`
- Test: `src/test/java/com/lzq/shortlink/controller/WorkspaceControllerTest.java`
- Test: `src/test/java/com/lzq/shortlink/controller/WorkspaceLinkIntegrationTest.java`

- [x] **Step 1: 写失败测试**：增加自定义短码持久化、保留路径拒绝和唯一冲突测试，确认异常类型尚不存在时先失败。
- [x] **Step 2: 增加请求校验**：自定义短码可选，长度 3~16，只允许数字、字母、下划线和连字符。
- [x] **Step 3: 实现业务策略**：保留路径大小写不敏感匹配；自定义短码唯一冲突返回 409，不改用随机短码掩盖冲突。
- [x] **Step 4: 保持随机短码兼容**：未传 `shortCode` 时沿用随机 Base62 生成与数据库唯一键重试。
- [ ] **Step 5: 联调验证**：由开发机运行真实数据库测试，确认自定义成功、重复返回 409、保留路径返回 400。
- [x] **Step 6: 更新路线日志**：记录短码长度、字符集、保留路径和待用户运行的联调命令。
