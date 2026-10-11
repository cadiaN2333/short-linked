package com.lzq.shortlink.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.message.VisitEventPublisher;
import com.lzq.shortlink.scheduler.VisitStatSyncScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 真实 Redis 协议和 TTL 验证；只操作本轮随机测试键，不清空数据库或缓存。 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "spring.rabbitmq.dynamic=false",
        "app.short-link.bloom.enabled=false",
        "app.security.jwt.secret=redis-cache-tests-only-not-for-production-2026"
})
class ShortLinkCacheRedisIntegrationTest {
    @Autowired StringRedisTemplate redis;
    @MockitoBean VisitEventPublisher publisher;
    @MockitoBean VisitStatSyncScheduler scheduler;
    private String code;

    @BeforeEach
    void setUp() { code = "rt" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }

    private ShortLinkRedirectCache cache(StringRedisTemplate template) {
        return new ShortLinkRedirectCache(template, Caffeine.newBuilder().build(), Caffeine.newBuilder().build());
    }

    private ShortLink row(String shortCode, String target) {
        ShortLink row = new ShortLink();
        row.setId(1L);
        row.setShortCode(shortCode);
        row.setOriginalUrl(target);
        row.setStatus("ACTIVE");
        return row;
    }

    @Test
    void atomicRecordsHaveRealBoundedTtlAndCaseSensitiveKeys() {
        String upper = code.toUpperCase(Locale.ROOT);
        var writer = cache(redis);
        writer.put(row(code, "https://example.com/lower"));
        Long ttl = redis.getExpire("short-link:cache:v2:" + code);
        assertNotNull(ttl);
        assertTrue(ttl >= 0 && ttl <= 30, "永久链接缓存也必须有有限 TTL");
        var coldReader = cache(redis);
        assertEquals("https://example.com/lower", coldReader.get(code).getOriginalUrl());
        assertNull(coldReader.get(upper));
        writer.put(row(upper, "https://example.com/upper"));
        assertEquals("https://example.com/upper", cache(redis).get(upper).getOriginalUrl());
        assertEquals("https://example.com/lower", cache(redis).get(code).getOriginalUrl());
    }

    @Test
    void injectedDeleteFailureDoesNotResurrectRealRedisValueAndCanRecover() {
        var faulty = spy(redis);
        var cache = cache(faulty);
        cache.put(row(code, "https://example.com/old"));
        doThrow(new RedisConnectionFailureException("模拟清理故障")).when(faulty).delete(anyString());
        cache.evict(code);
        assertNull(cache.get(code));
        cache.put(row(code, "https://example.com/new"));
        assertEquals("https://example.com/new", cache.get(code).getOriginalUrl());
        doCallRealMethod().when(faulty).delete(anyString());
        cache.evict(code);
        cache.put(row(code, "https://example.com/new"));
        assertEquals("https://example.com/new", cache(redis).get(code).getOriginalUrl());
    }

    @AfterEach
    void removeOnlyThisTestsKeys() {
        String upper = code.toUpperCase(Locale.ROOT);
        List<String> keys = List.of("short-link:cache:v2:" + code, "short-link:cache-missing:v2:" + code,
                "short-link:cache:v2:" + upper, "short-link:cache-missing:v2:" + upper);
        redis.delete(keys);
        for (String key : keys) assertFalse(Boolean.TRUE.equals(redis.hasKey(key)));
    }
}
