package com.lzq.shortlink.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.lzq.shortlink.cache.ShortCodeBloomFilter;
import com.lzq.shortlink.config.ShortLinkBloomProperties;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.exception.InvalidTargetUrlException;
import com.lzq.shortlink.exception.ShortCodeAlreadyExistsException;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import com.lzq.shortlink.message.VisitEventPublisher;
import com.lzq.shortlink.service.impl.ShortLinkServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实服务与真实过滤器联动；数据库、Redis 和消息发送边界使用替身。 */
class ShortLinkBloomFlowTest {
    private ShortCodeBloomFilter bloom;
    private ShortLinkMapper mapper;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ShortLinkServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        bloom = new ShortCodeBloomFilter(new ShortLinkBloomProperties(true, 10000, .001, 1000));
        mapper = mock(ShortLinkMapper.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = serviceWith(bloom);
    }

    private ShortLinkServiceImpl serviceWith(ShortCodeBloomFilter filter) {
        return new ShortLinkServiceImpl(redis, mapper, mock(VisitEventPublisher.class),
                Caffeine.newBuilder().build(), Caffeine.newBuilder().build(), filter);
    }

    @Test
    void definiteMissingSkipsDatabaseAndDoesNotWriteNegativeKeys() {
        bloom.initialize(add -> {});
        assertNull(service.findAvailableShortLink("missing123"));
        verifyNoInteractions(mapper);
        verify(values, never()).set(anyString(), anyString(), any(java.time.Duration.class));
    }

    @Test
    void positiveRedisCacheIsNotRejectedByEmptyBloom() {
        bloom.initialize(add -> {});
        when(values.get("short-link:cache:v2:cached123")).thenReturn(
                "{\"version\":2,\"id\":1,\"shortCode\":\"cached123\",\"originalUrl\":\"https://example.com\",\"status\":\"ACTIVE\",\"expireAt\":\"\"}");
        assertNotNull(service.findAvailableShortLink("cached123"));
        verifyNoInteractions(mapper);
    }

    @Test
    void redisFailureStillAllowsBloomToProtectDatabase() {
        bloom.initialize(add -> {});
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("模拟 Redis 断开"));
        assertNull(service.findAvailableShortLink("missing123"));
        verifyNoInteractions(mapper);
    }

    @Test
    void unreadyAndFailedBloomAllowDatabaseLookup() {
        service.findAvailableShortLink("before123");
        bloom.initialize(add -> { throw new IllegalStateException("模拟初始化失败"); });
        service.findAvailableShortLink("after123");
        verify(mapper, times(2)).selectOne(any());
    }

    @Test
    void possibleExistenceStillChecksDatabaseAndExpiry() {
        bloom.initialize(add -> add.accept("expired123"));
        ShortLink row = new ShortLink();
        row.setShortCode("expired123");
        row.setStatus("ACTIVE");
        row.setExpireAt(LocalDateTime.now().minusMinutes(1));
        when(mapper.selectOne(any())).thenReturn(row);
        assertNull(service.findAvailableShortLink("expired123"));
        verify(mapper).selectOne(any());
    }

    @Test
    void customAndRandomCreationRegisterBeforeEachInsert() {
        bloom.initialize(add -> {});
        doAnswer(invocation -> {
            ShortLink row = invocation.getArgument(0);
            assertTrue(bloom.mightContain(row.getShortCode()), "插入前必须完成登记");
            return 1;
        }).when(mapper).insert(any(ShortLink.class));
        service.createShortLink(1L, "https://example.com", null, "new123");
        service.createShortLink(1L, "https://example.com", null);
        verify(mapper, times(2)).insert(any(ShortLink.class));
    }

    @Test
    void randomCollisionRegistersEveryCandidate() {
        bloom.initialize(add -> {});
        int[] attempts = {0};
        doAnswer(invocation -> {
            ShortLink row = invocation.getArgument(0);
            assertTrue(bloom.mightContain(row.getShortCode()));
            if (++attempts[0] < 3) throw new DuplicateKeyException("模拟碰撞");
            return 1;
        }).when(mapper).insert(any(ShortLink.class));
        assertNotNull(service.createShortLink(1L, "https://example.com", null));
        assertEquals(3, attempts[0]);
    }

    @Test
    void duplicateCustomCodeDoesNotRemoveMembership() {
        bloom.initialize(add -> {});
        when(mapper.insert(any(ShortLink.class))).thenThrow(new DuplicateKeyException("模拟重复"));
        assertThrows(ShortCodeAlreadyExistsException.class,
                () -> service.createShortLink(1L, "https://example.com", null, "new123"));
        assertTrue(bloom.mightContain("new123"));
    }

    @Test
    void invalidUrlDoesNotPolluteFilterOrDatabase() {
        bloom.initialize(add -> {});
        assertThrows(InvalidTargetUrlException.class,
                () -> service.createShortLink(1L, "javascript:alert(1)", null, "new123"));
        assertFalse(bloom.mightContain("new123"));
        verifyNoInteractions(mapper);
    }

    @Test
    void initializationWaitsForInFlightDatabaseInsert() throws Exception {
        CountDownLatch inserting = new CountDownLatch(1);
        CountDownLatch releaseInsert = new CountDownLatch(1);
        CountDownLatch initializing = new CountDownLatch(1);
        AtomicReference<String> committed = new AtomicReference<>();
        doAnswer(invocation -> {
            inserting.countDown();
            if (!releaseInsert.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("模拟插入超时");
            ShortLink row = invocation.getArgument(0);
            committed.set(row.getShortCode());
            return 1;
        }).when(mapper).insert(any(ShortLink.class));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var creation = executor.submit(() -> service.createShortLink(1L, "https://example.com", null, "new123"));
            assertTrue(inserting.await(5, TimeUnit.SECONDS));
            var initialization = executor.submit(() -> {
                initializing.countDown();
                bloom.initialize(add -> { if (committed.get() != null) add.accept(committed.get()); });
            });
            assertTrue(initializing.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> initialization.get(100, TimeUnit.MILLISECONDS));
            releaseInsert.countDown();
            creation.get(5, TimeUnit.SECONDS);
            initialization.get(5, TimeUnit.SECONDS);
            assertTrue(bloom.isReady());
            assertTrue(bloom.mightContain("new123"));
        } finally {
            releaseInsert.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void fixedSeedMissingCodesReduceDatabaseLookups() {
        // 同一批互异请求分别使用独立服务和空缓存，避免负缓存预热干扰。
        bloom.initialize(add -> { for (int i = 0; i < 10000; i++) add.accept("known" + i); });
        Random random = new Random(20261011);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 100000; i++) codes.add("m" + Integer.toString(i, 36) + "_" + Integer.toString(random.nextInt(1000000), 36));
        var baseline = serviceWith(ShortCodeBloomFilter.disabled());
        for (String code : codes) baseline.findAvailableShortLink(code);
        verify(mapper, times(100000)).selectOne(any());
        clearInvocations(mapper, values, redis);
        for (String code : codes) service.findAvailableShortLink(code);
        long reads = mockingDetails(mapper).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("selectOne")).count();
        assertTrue(reads <= 1000, "开启过滤后数据库查询应减少至少 99%，实际查询=" + reads);
        System.out.printf("布隆对照测试：请求=%d，关闭查询=%d，开启查询=%d%n", codes.size(), 100000, reads);
    }
}
