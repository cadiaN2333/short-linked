# Redis 跳转缓存实施计划

> **供执行代理使用：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行；步骤采用复选框记录进度。

**目标：** 为短链接跳转查询增加 Redis Cache-Aside 缓存，缓存命中时避免查询 MySQL。

**架构：** `ShortLinkServiceImpl` 使用 `StringRedisTemplate` 读取键 `short-link:{shortCode}`。未命中时查询 MySQL，验证链接未过期后回填 Redis；临期链接的 Redis TTL 与其剩余有效期一致。Controller 不感知缓存实现，仍只负责返回 302 或 404。

**技术栈：** Spring Boot、Spring Data Redis、Redis Docker 容器、MyBatis-Plus、MySQL。

---

## 文件结构

- 修改：`pom.xml`：引入 Redis Starter。
- 修改：`src/main/resources/application-local.yaml`：添加本机 Redis 地址与端口，不提交 Git。
- 修改：`src/main/resources/application-local.yaml.example`：添加不含凭据的 Redis 配置模板，可提交 Git。
- 修改：`src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`：增加 Redis 读取、回源与回填。

### 任务 1：添加 Redis 依赖和本机连接配置

**文件：**

- 修改：`pom.xml`
- 修改：`src/main/resources/application-local.yaml`
- 修改：`src/main/resources/application-local.yaml.example`

- [ ] **步骤 1：在 `pom.xml` 的 `<dependencies>` 中加入 Spring Data Redis Starter。**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

- [ ] **步骤 2：在本机 `application-local.yaml` 的 `spring` 节点下加入 Redis 连接配置。**

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

保留文件中已有的 `spring.datasource` 节点，将 `data` 与 `datasource` 放在同一个 `spring` 节点下。

- [ ] **步骤 3：在 `application-local.yaml.example` 加入同样的 Redis 示例配置。**

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

- [ ] **步骤 4：重新加载 Maven，并启动 `ShortLinkApplication`。**

预期：应用正常启动；Docker Desktop 中 Redis 容器继续显示运行，端口映射为 `6379:6379`。

### 任务 2：在跳转查询中实现 Cache-Aside

**文件：**

- 修改：`src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`

- [ ] **步骤 1：为 `ShortLinkServiceImpl` 增加依赖和常量。**

```java
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
```

```java
private static final String REDIS_KEY_PREFIX = "short-link:";

private final StringRedisTemplate stringRedisTemplate;

public ShortLinkServiceImpl(
        ShortLinkMapper shortLinkMapper,
        StringRedisTemplate stringRedisTemplate
) {
    this.shortLinkMapper = shortLinkMapper;
    this.stringRedisTemplate = stringRedisTemplate;
}
```

- [ ] **步骤 2：将 `findAvailableShortLink` 替换为缓存优先的实现。**

```java
@Override
public ShortLink findAvailableShortLink(String shortCode) {
    String cacheKey = REDIS_KEY_PREFIX + shortCode;
    String originalUrl = stringRedisTemplate.opsForValue().get(cacheKey);

    if (originalUrl != null) {
        ShortLink cachedShortLink = new ShortLink();
        cachedShortLink.setShortCode(shortCode);
        cachedShortLink.setOriginalUrl(originalUrl);
        return cachedShortLink;
    }

    ShortLink shortLink = shortLinkMapper.selectOne(
            new LambdaQueryWrapper<ShortLink>()
                    .eq(ShortLink::getShortCode, shortCode)
    );

    if (shortLink == null) {
        return null;
    }

    if (shortLink.getExpireAt() != null) {
        Duration remaining = Duration.between(
                LocalDateTime.now(),
                shortLink.getExpireAt()
        );

        if (remaining.isZero() || remaining.isNegative()) {
            return null;
        }

        stringRedisTemplate.opsForValue().set(
                cacheKey,
                shortLink.getOriginalUrl(),
                remaining
        );
    } else {
        stringRedisTemplate.opsForValue().set(
                cacheKey,
                shortLink.getOriginalUrl()
        );
    }

    return shortLink;
}
```

- [ ] **步骤 3：运行已有短链接创建与跳转测试，确认构造器注入没有破坏 Spring Bean 创建。**

运行：在 IntelliJ 分别运行 `ShortLinkServiceTest` 与 `ShortLinkControllerTest`。

预期：测试通过；Redis 容器处于运行状态时，应用不会报 Redis 连接异常。

### 任务 3：手动确认缓存回填和命中

**文件：**

- 不修改文件。

- [ ] **步骤 1：通过 Apifox 创建一个有效短链接并记录返回的 `shortCode`。**

```http
POST http://localhost:8080/api/links
Content-Type: application/json
```

```json
{
  "originalUrl": "https://www.baidu.com"
}
```

- [ ] **步骤 2：首次访问 `GET http://localhost:8080/{shortCode}`，触发 MySQL 回源和 Redis 回填。**

预期：返回 302，响应头 `Location` 为 `https://www.baidu.com`。

- [ ] **步骤 3：在 Docker Desktop 的 Redis 容器终端执行缓存查询。**

```sh
redis-cli GET short-link:{shortCode}
```

预期：输出 `https://www.baidu.com`。

- [ ] **步骤 4：再次访问同一短链接，确认仍返回 302。**

预期：请求不需要再从 MySQL 读取原始链接；后续可通过日志或断点观察缓存命中。

- [ ] **步骤 5：提交公开代码和示例配置。**

```powershell
git add pom.xml src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java src/main/resources/application-local.yaml.example
git commit -m "feat: 增加短链接跳转 Redis 缓存"
```

预期：`application-local.yaml` 不会出现在提交中。
