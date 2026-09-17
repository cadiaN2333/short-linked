# Redis 缓存故障降级实施计划

> **供代理执行者使用：** 必须使用 `subagent-driven-development`（推荐）或 `executing-plans` 子技能逐任务执行。本计划使用复选框跟踪步骤。

**目标：** Redis 临时不可用时，短链接跳转回源 MySQL 并继续返回正确的 302 或 404。

**架构：** `ShortLinkServiceImpl` 只在 Redis 读写调用的边界捕获 `RedisConnectionFailureException`。读取失败视为缓存未命中；写入失败只记录警告。数据库查询、过期判断和 Controller 的 302/404 行为保持不变。

**技术栈：** Java 17、Spring Boot、Spring Data Redis、MyBatis-Plus、MySQL、JUnit 5。

---

## 文件结构

- 修改 `src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`：为 Redis 读取与写入加入降级分支。
- 修改 `src/test/java/com/lzq/shortlink/service/ShortLinkServiceTest.java`：适配 `ShortLinkServiceImpl` 已加入的 `StringRedisTemplate` 构造参数，保证现有测试源文件可编译。
- 不创建新测试类：按用户明确要求，本阶段使用手动停启 Redis 容器验收降级行为。

### 任务 1：为 Redis 读取和写入增加连接故障降级

**文件：**
- 修改：`src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`

- [ ] **步骤 1：添加 Redis 连接异常的导入**

在现有 Redis 导入下方添加：

```java
import org.springframework.data.redis.RedisConnectionFailureException;
```

- [ ] **步骤 2：将 Redis 读取替换为可降级读取**

将 `findAvailableShortLink` 开头的直接 `get` 调用替换为：

```java
String originalUrl = null;

try {
    originalUrl = stringRedisTemplate.opsForValue().get(cacheKey);
} catch (RedisConnectionFailureException exception) {
    log.warn(
            "Redis 读取失败，已降级查询 MySQL，shortCode={}",
            shortCode,
            exception
    );
}
```

后续的 `if (originalUrl != null)` 缓存命中逻辑保持不变。

- [ ] **步骤 3：将两处 Redis 写入包裹在同一个降级分支中**

保留原有的 `remaining` 计算和过期判断；将其后两处分支中的 `set` 调用改为：

```java
try {
    if (shortLink.getExpireAt() != null) {
        stringRedisTemplate.opsForValue().set(
                cacheKey,
                shortLink.getOriginalUrl(),
                remaining
        );
        log.info("短链接缓存剩余有效期，shortCode={}, remaining={}",
                shortCode, remaining);
    } else {
        stringRedisTemplate.opsForValue().set(
                cacheKey,
                shortLink.getOriginalUrl()
        );
        log.info("短链接缓存永久有效期，shortCode={}", shortCode);
    }
} catch (RedisConnectionFailureException exception) {
    log.warn(
            "Redis 写入失败，本次请求仍使用 MySQL 结果，shortCode={}",
            shortCode,
            exception
    );
}
```

`remaining` 需要在 `if (shortLink.getExpireAt() != null)` 外提前声明：

```java
Duration remaining = null;

if (shortLink.getExpireAt() != null) {
    remaining = Duration.between(LocalDateTime.now(), shortLink.getExpireAt());

    if (remaining.isZero() || remaining.isNegative()) {
        return null;
    }
}
```

- [ ] **步骤 4：保存并启动应用**

运行 `ShortLinkApplication`。Redis 正常运行时，访问已缓存短码应仍看到“短链接缓存命中”日志并返回 302。

### 任务 2：适配既有测试类的构造参数

**文件：**
- 修改：`src/test/java/com/lzq/shortlink/service/ShortLinkServiceTest.java`

- [ ] **步骤 1：添加导入和 Spring 注入字段**

添加导入：

```java
import org.springframework.data.redis.core.StringRedisTemplate;
```

在 `shortLinkMapper` 字段后添加：

```java
@Autowired
private StringRedisTemplate stringRedisTemplate;
```

- [ ] **步骤 2：向固定短码测试服务传递 Redis 模板**

将创建 `FixedShortCodeService` 的代码改为：

```java
ShortLinkService fixedShortCodeService = new FixedShortCodeService(
        shortLinkMapper,
        stringRedisTemplate,
        conflictedCode,
        availableCode
);
```

将内部类构造方法改为：

```java
private FixedShortCodeService(
        ShortLinkMapper shortLinkMapper,
        StringRedisTemplate stringRedisTemplate,
        String... shortCodes
) {
    super(stringRedisTemplate, shortLinkMapper);
    this.shortCodes.addAll(java.util.List.of(shortCodes));
}
```

### 任务 3：手动验收 Redis 不可用时的跳转

**文件：**
- 不修改文件。

- [ ] **步骤 1：确认数据库中存在一个有效短码**

使用已创建的短码，例如 `dYSy3G4r`，其目标地址应为一个可跳转的 HTTP/HTTPS 链接。

- [ ] **步骤 2：停止 Redis 容器**

在 PowerShell 执行：

```powershell
docker stop redis
```

预期输出：`redis`。

- [ ] **步骤 3：在 Apifox 访问短链接**

请求：

```text
GET http://localhost:8080/dYSy3G4r
```

预期：HTTP 302，`Location` 为该短码对应的原始链接；应用控制台含“Redis 读取失败，已降级查询 MySQL”。

- [ ] **步骤 4：恢复 Redis 容器**

在 PowerShell 执行：

```powershell
docker start redis
```

预期输出：`redis`。再次访问同一短码后，缓存会重新回填。

- [ ] **步骤 5：提交本阶段代码**

```powershell
git -C 'D:\短链接\short-link' add -- src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java src/test/java/com/lzq/shortlink/service/ShortLinkServiceTest.java
git -C 'D:\短链接\short-link' commit -m 'feat: Redis 不可用时降级查询 MySQL'
```
