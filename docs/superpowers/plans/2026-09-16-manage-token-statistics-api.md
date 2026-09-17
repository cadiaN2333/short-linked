# 管理凭证统计查询接口实施计划

> **供代理执行者使用：** 必须使用 `subagent-driven-development`（推荐）或 `executing-plans` 子技能逐任务执行。本计划使用复选框跟踪步骤。

**目标：** 创建短链接时生成并返回管理凭证，使用该凭证查询短链接的已落库访问统计。

**架构：** `manageToken` 是创建者持有的随机管理凭证，保存在 `short_link` 表中。创建接口只返回一次凭证；统计接口通过 `X-Manage-Token` 请求头与短码联合查询，任一不匹配统一返回 404。统计读取 MySQL，保持与 Redis 异步统计相同的最终一致语义。

**技术栈：** Java 17、Spring Boot、Spring Web、MyBatis-Plus、MySQL、JUnit 5。

---

## 文件结构

- 创建 `docs/sql/2026-09-16-add-manage-token.sql`：一次性数据库迁移脚本。
- 修改 `entity/ShortLink.java`：映射管理凭证字段。
- 修改 `dto/CreateShortLinkResponse.java`：创建响应返回凭证。
- 创建 `dto/ShortLinkStatisticsResponse.java`：统计查询响应。
- 修改 `service/ShortLinkService.java` 和 `service/impl/ShortLinkServiceImpl.java`：生成凭证并执行短码与凭证联合查询。
- 修改 `controller/ShortLinkController.java`：暴露受请求头保护的统计查询接口。
- 修改 `test/ShortLinkServiceTest.java`：覆盖令牌生成和联合查询。

### 任务 1：添加数据库迁移与实体字段

**文件：**
- 创建：`docs/sql/2026-09-16-add-manage-token.sql`。
- 修改：`src/main/java/com/lzq/shortlink/entity/ShortLink.java`。

- [ ] **步骤 1：创建迁移脚本**

```sql
ALTER TABLE short_link
    ADD COLUMN manage_token CHAR(32) NULL COMMENT '管理凭证' AFTER original_url;

UPDATE short_link
SET manage_token = LOWER(REPLACE(UUID(), '-', ''))
WHERE manage_token IS NULL;

ALTER TABLE short_link
    MODIFY COLUMN manage_token CHAR(32) NOT NULL COMMENT '管理凭证',
    ADD UNIQUE KEY uk_manage_token (manage_token);
```

- [ ] **步骤 2：在 `ShortLink` 添加字段**

```java
/** 创建者持有的管理凭证。 */
private String manageToken;
```

### 任务 2：生成并返回管理凭证

**文件：**
- 修改：`src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`。
- 修改：`src/main/java/com/lzq/shortlink/dto/CreateShortLinkResponse.java`。
- 修改：`src/main/java/com/lzq/shortlink/controller/ShortLinkController.java`。

- [ ] **步骤 1：创建链接时设置凭证**

```java
shortLink.setManageToken(generateManageToken());
```

```java
private String generateManageToken() {
    return UUID.randomUUID().toString().replace("-", "");
}
```

- [ ] **步骤 2：创建响应增加字段**

```java
/** 查询统计使用的管理凭证，只在创建时返回一次。 */
private String manageToken;
```

- [ ] **步骤 3：Controller 返回令牌**

```java
response.setManageToken(shortLink.getManageToken());
```

### 任务 3：提供受凭证保护的统计查询

**文件：**
- 创建：`src/main/java/com/lzq/shortlink/dto/ShortLinkStatisticsResponse.java`。
- 修改：`src/main/java/com/lzq/shortlink/service/ShortLinkService.java`。
- 修改：`src/main/java/com/lzq/shortlink/service/impl/ShortLinkServiceImpl.java`。
- 修改：`src/main/java/com/lzq/shortlink/controller/ShortLinkController.java`。

- [ ] **步骤 1：定义统计响应 DTO**

```java
@Data
public class ShortLinkStatisticsResponse {
    private String shortCode;
    private String originalUrl;
    private Long visitCount;
    private LocalDateTime lastVisitedAt;
}
```

- [ ] **步骤 2：声明联合查询方法**

```java
ShortLink findShortLinkForStatistics(String shortCode, String manageToken);
```

- [ ] **步骤 3：实现联合查询**

```java
if (manageToken == null || manageToken.isBlank()) {
    return null;
}

return shortLinkMapper.selectOne(
        new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getShortCode, shortCode)
                .eq(ShortLink::getManageToken, manageToken)
);
```

- [ ] **步骤 4：新增统计接口**

```java
@GetMapping("/{shortCode}/stats")
public ShortLinkStatisticsResponse getStatistics(
        @PathVariable String shortCode,
        @RequestHeader(value = "X-Manage-Token", required = false) String manageToken
) {
    ShortLink shortLink = shortLinkService.findShortLinkForStatistics(
            shortCode,
            manageToken
    );

    if (shortLink == null) {
        throw new ShortLinkNotFoundException();
    }

    ShortLinkStatisticsResponse response = new ShortLinkStatisticsResponse();
    response.setShortCode(shortLink.getShortCode());
    response.setOriginalUrl(shortLink.getOriginalUrl());
    response.setVisitCount(shortLink.getVisitCount());
    response.setLastVisitedAt(shortLink.getLastVisitedAt());
    return response;
}
```

### 任务 4：适配自动化测试并手动验收

**文件：**
- 修改：`src/test/java/com/lzq/shortlink/service/ShortLinkServiceTest.java`。

- [ ] **步骤 1：验证创建时生成 32 位令牌**

```java
assertNotNull(createdShortLink.getManageToken());
assertEquals(32, createdShortLink.getManageToken().length());
```

- [ ] **步骤 2：验证正确与错误凭证的联合查询**

```java
ShortLink matchedShortLink = shortLinkService.findShortLinkForStatistics(
        createdShortLink.getShortCode(),
        createdShortLink.getManageToken()
);
assertNotNull(matchedShortLink);

ShortLink unmatchedShortLink = shortLinkService.findShortLinkForStatistics(
        createdShortLink.getShortCode(),
        "invalid-token"
);
assertNull(unmatchedShortLink);
```

- [ ] **步骤 3：在 Apifox 验收**

```text
POST /api/links
```

从响应保存 `shortCode` 和 `manageToken`，然后发送：

```text
GET /api/links/{shortCode}/stats
X-Manage-Token: {manageToken}
```

预期 HTTP 200；不带或携带错误令牌预期 HTTP 404。

- [ ] **步骤 4：提交代码和迁移脚本**

```powershell
git -C 'D:\短链接\short-link' add -- docs/sql/2026-09-16-add-manage-token.sql src/main/java src/test/java
git -C 'D:\短链接\short-link' commit -m "feat: 添加短链接管理凭证和统计查询"
```
