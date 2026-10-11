package com.lzq.shortlink.controller;

import com.lzq.shortlink.auth.AuthService;
import com.lzq.shortlink.cache.ShortCodeBloomFilter;
import com.lzq.shortlink.cache.ShortCodeBloomInitializer;
import com.lzq.shortlink.config.ShortLinkBloomProperties;
import com.lzq.shortlink.auth.JwtProperties;
import com.lzq.shortlink.auth.dto.RegisterRequest;
import com.lzq.shortlink.entity.AppUser;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import com.lzq.shortlink.message.VisitEventPublisher;
import com.lzq.shortlink.scheduler.VisitStatSyncScheduler;
import com.lzq.shortlink.workspace.WorkspaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实安全链、业务服务和 MySQL 联调；禁用迁移、真实消费和 Redis 写入，测试事务自动回滚。 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "spring.rabbitmq.dynamic=false",
        "app.security.jwt.secret=url-validation-tests-only-not-for-production-2026"
})
@AutoConfigureMockMvc
@Transactional
class TargetUrlValidationDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired WorkspaceMapper workspaces;
    @Autowired ShortLinkMapper links;
    @Autowired JwtEncoder encoder;
    @Autowired JwtProperties jwtProperties;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean VisitEventPublisher publisher;
    @MockitoBean VisitStatSyncScheduler scheduler;

    private Long workspaceId;
    private String bearer;
    private String email;
    private String code;
    private ValueOperations<String, String> values;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        email = "url-check-" + UUID.randomUUID() + "@example.com";
        code = "uv" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        RegisterRequest registration = new RegisterRequest();
        registration.setEmail(email);
        registration.setPassword("UrlTest@123456");
        AppUser user = auth.register(registration);
        workspaceId = workspaces.selectActiveByUserId(user.getId()).get(0).getId();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(jwtProperties.getIssuer())
                .subject(user.getId().toString()).issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        bearer = "Bearer " + encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String path() {
        return "/api/v1/workspaces/" + workspaceId + "/links";
    }

    private String body(String url) {
        return json.writeValueAsString(Map.of("originalUrl", url, "shortCode", code));
    }

    @Test
    void bloomHonorsActualDatabaseComparisonWithoutChangingSchema() {
        String collation = links.selectShortCodeCollation();
        assertNotNull(collation, "必须能够查询实际数据库比较规则");
        var properties = new ShortLinkBloomProperties(true, 1000000, .001, 1000);
        var filter = new ShortCodeBloomFilter(properties);
        new ShortCodeBloomInitializer(filter, properties, links).run(null);
        String normalized = collation.toLowerCase(java.util.Locale.ROOT);
        if (!normalized.equals("binary") && !normalized.endsWith("_bin")) {
            assertFalse(filter.isReady(), "未支持的比较规则不能开启负拦截");
            assertTrue(filter.mightContain("missing123"));
        } else {
            assertTrue(filter.isReady(), "迁移后的历史短码应可完整初始化布隆过滤器");
        }
        System.out.printf("真实数据库布隆检查：short_code collation=%s，READY=%s%n", collation, filter.isReady());
    }

    @Test
    void invalidCreateLeavesDatabaseEmpty() throws Exception {
        for (String url : new String[]{"javascript:alert(1)", "https://u:p@example.com", "https://example.com/%0a"}) {
            mvc.perform(post(path()).header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(body(url)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TARGET_URL"));
        }
        assertEquals(0, links.countByWorkspaceId(workspaceId));
        verifyNoInteractions(publisher);
    }

    @Test
    void createEditAndRedirectUseSameValidatedUrl() throws Exception {
        String response = mvc.perform(post(path()).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body("HTTPS://EXAMPLE.COM/old?q=%26")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/old?q=%26"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();
        assertEquals("https://example.com/old?q=%26", links.selectByWorkspaceIdAndId(workspaceId, id).getOriginalUrl());
        mvc.perform(get("/" + code)).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/old?q=%26"));
        mvc.perform(put(path() + "/" + id).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body("https://u:p@example.com")))
                .andExpect(status().isBadRequest());
        assertEquals("https://example.com/old?q=%26", links.selectByWorkspaceIdAndId(workspaceId, id).getOriginalUrl());
        mvc.perform(put(path() + "/" + id).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body("HTTPS://EXAMPLE.COM/new?q=a+b")))
                .andExpect(status().isOk());
        assertEquals("https://example.com/new?q=a+b", links.selectByWorkspaceIdAndId(workspaceId, id).getOriginalUrl());
        // 此前已预热本地缓存，编辑后的跳转必须使用新地址。
        mvc.perform(get("/" + code)).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/new?q=a+b"));
    }

    @Test
    void historicalMalformedRowCannotRedirectOrGenerateStats() throws Exception {
        ShortLink link = new ShortLink();
        link.setWorkspaceId(workspaceId);
        link.setShortCode(code);
        link.setOriginalUrl("javascript:alert(1)");
        link.setStatus("ACTIVE");
        links.insert(link);
        mvc.perform(get("/" + code)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"))
                .andExpect(header().doesNotExist("Location"));
        verify(values, never()).increment(anyString());
        verifyNoInteractions(publisher);
    }

    @AfterTransaction
    void verifiesTestDataWasRolledBack() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = ?", Integer.class, email));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM short_link WHERE short_code = ?", Integer.class, code));
        assertEquals(0, links.countByWorkspaceId(workspaceId));
    }

    @Test
    void differentlyCasedCodesAreIndependentAndWrongCaseReturns404() throws Exception {
        String lower = code.toLowerCase(java.util.Locale.ROOT);
        String upper = code.toUpperCase(java.util.Locale.ROOT);
        mvc.perform(post(path()).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("originalUrl", "https://example.com/lower", "shortCode", lower))))
                .andExpect(status().isOk());
        mvc.perform(get("/" + upper)).andExpect(status().isNotFound());
        mvc.perform(get("/" + lower)).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/lower"));
        mvc.perform(post(path()).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("originalUrl", "https://example.com/upper", "shortCode", upper))))
                .andExpect(status().isOk());
        mvc.perform(get("/" + upper)).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/upper"));
        mvc.perform(get("/" + lower)).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/lower"));
        assertEquals(2, links.countByWorkspaceId(workspaceId));
    }
}
