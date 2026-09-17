# 实习项目可靠性增强实施计划

> **For agentic workers:** 按测试驱动开发逐项执行；每项先写失败测试，再实现最小代码，最后运行对应测试。

**目标：** 在不过度引入 Outbox、exactly-once 或监控平台的前提下，补齐实习项目需要展示的消息投递保护和短链接缓存防护。

**架构：** 短链接跳转继续保持 Redis 优先、MySQL 降级，RabbitMQ 访问事件异步处理。RabbitMQ 使用发布确认、不可路由消息回调和有限重试；缓存同时保存短链状态，异常短码使用短 TTL 负缓存。

**技术栈：** Java 17、Spring Boot 4、Spring AMQP、RabbitMQ、Redis、MySQL、JUnit 5、Mockito。

---

### 任务 1：记录实习项目范围和暂缓项

**文件：**
- 修改：`docs/访问统计与消息链路可靠性复盘.md`
- 修改：`docs/短链接系统开发路线.md`

- [x] 记录 Outbox、exactly-once、DLQ 自动重放、链路追踪、Prometheus/Grafana、多环境 Key、时区重构、旧接口下线、压测的后续方案和暂缓原因。

### 任务 2：补充 RabbitMQ 发布确认和不可路由保护

**文件：**
- 修改：`src/main/resources/application.yaml`
- 修改：`src/main/java/com/lzq/shortlink/config/RabbitMqConfig.java`
- 修改：`src/main/java/com/lzq/shortlink/message/VisitEventPublisher.java`
- 修改：`src/test/java/com/lzq/shortlink/config/RabbitMqConfigTest.java`
- 修改：`src/test/java/com/lzq/shortlink/message/VisitEventPublisherTest.java`

- [x] 添加失败测试：发布器携带事件 ID 的 `CorrelationData`；确认 NACK 和 returned message 不抛出到跳转主流程。
- [x] 运行测试确认缺少 `RabbitTemplateCustomizer` 时先失败，再修正测试调用签名。
- [x] 开启 correlated confirm、publisher returns 和 mandatory；通过 `RabbitTemplateCustomizer` 注册确认/退回回调。
- [x] 使用 `CorrelationData` 发布事件；失败只记录结构化告警，不阻断 302 跳转。
- [x] 运行 `mvn -q "-Dtest=RabbitMqConfigTest,VisitEventPublisherTest" test`，4 项测试通过。

### 任务 3：缓存状态校验、缓存值保护和负缓存

**文件：**
- 修改：`src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`
- 修改：`src/test/java/com/lzq/shortlink/service/ShortLinkWorkspaceServiceTest.java`

- [x] 添加失败测试：缓存值包含 `DISABLED` 时不返回可跳转对象；缓存 ID 非数字时删除坏缓存并回源；MySQL 不存在短码时写入短 TTL 负缓存。
- [x] 运行测试确认原实现出现 2 个断言失败和 1 个 `NumberFormatException`。
- [x] 将 URL、ID、状态统一写入缓存；命中时只允许 `ACTIVE`；捕获 `NumberFormatException` 后删除坏缓存并回源。
- [x] 为不存在短码写入 30 秒负缓存；创建或更新短码时删除对应负缓存。
- [x] 将热路径命中/未命中/计数日志降到 DEBUG。
- [x] 运行 `mvn -q "-Dtest=ShortLinkWorkspaceServiceTest" test`，13 项测试通过。

### 任务 4：复盘和 Git 交付

**文件：**
- 修改：`docs/访问统计与消息链路可靠性复盘.md`
- 修改：`docs/短链接系统开发路线.md`

- [ ] 写入实际完成项、未完成边界和用户需要执行的 RabbitMQ/Redis 联调命令。
- [ ] 运行 `git diff --check`、相关单测和快速编译。
- [ ] 检查 `git status` 与 `git diff`，只提交本轮相关文件。
- [ ] 提交本地 Git；确认 `origin` 后尝试推送。若远端权限或网络失败，保留提交并给出用户可执行命令。
