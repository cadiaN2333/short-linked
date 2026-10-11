package com.lzq.shortlink.service;

import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import com.lzq.shortlink.message.VisitEventPublisher;
import com.lzq.shortlink.service.impl.ShortLinkServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 严格大小写与缓存恢复的业务回归测试。 */
class ShortLinkCacheConsistencyTest {
    private ShortLinkMapper mapper;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ShortLinkServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(ShortLinkMapper.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new ShortLinkServiceImpl(redis, mapper, mock(VisitEventPublisher.class));
    }

    private ShortLink row(String code, String url) {
        ShortLink row = new ShortLink();
        row.setId(1L);
        row.setWorkspaceId(1L);
        row.setShortCode(code);
        row.setStatus("ACTIVE");
        row.setOriginalUrl(url);
        return row;
    }

    @Test
    void differentlyCasedDatabaseResultMustNotRedirect() {
        // 模拟尚未迁移的大小写不敏感数据库，业务层仍须严格校验。
        when(mapper.selectOne(any())).thenReturn(row("AbC123", "https://example.com"));
        assertNull(service.findAvailableShortLink("abc123"));
    }

    @Test
    void oldRedisProtocolMustNotServeCaseAliases() {
        when(values.get("short-link:abc123")).thenReturn("https://example.com/old");
        when(values.get("short-link:id:abc123")).thenReturn("1");
        when(values.get("short-link:status:abc123")).thenReturn("ACTIVE");
        when(values.get("short-link:expire-at:abc123")).thenReturn("");
        assertNull(service.findAvailableShortLink("abc123"));
        verify(mapper).selectOne(any());
    }

    @Test
    void permanentLinksMustUseBoundedAtomicRedisRecord() {
        when(mapper.selectOne(any())).thenReturn(row("AbC123", "https://example.com"));
        assertNotNull(service.findAvailableShortLink("AbC123"));
        verify(values).set(eq("short-link:cache:v2:AbC123"), anyString(), eq(Duration.ofSeconds(30)));
        verify(values, never()).set(anyString(), anyString());
    }

    @Test
    void deletionFailureMustNotResurrectOldRedisValue() {
        ShortLink current = row("AbC123", "https://example.com/old");
        when(mapper.selectByWorkspaceIdAndId(1L, 1L)).thenReturn(current);
        when(mapper.selectOne(any())).thenAnswer(invocation -> current);
        when(mapper.updateContentByWorkspaceIdAndId(eq(1L), eq(1L), anyString(), isNull()))
                .thenAnswer(invocation -> { current.setOriginalUrl(invocation.getArgument(2)); return 1; });
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("模拟删除失败"));
        String stale = "{\"version\":2,\"id\":1,\"shortCode\":\"AbC123\",\"originalUrl\":\"https://example.com/old\",\"status\":\"ACTIVE\",\"expireAt\":\"\"}";
        when(values.get("short-link:cache:v2:AbC123")).thenReturn(stale);
        when(values.get("short-link:AbC123")).thenReturn("https://example.com/old");
        when(values.get("short-link:id:AbC123")).thenReturn("1");
        when(values.get("short-link:status:AbC123")).thenReturn("ACTIVE");
        when(values.get("short-link:expire-at:AbC123")).thenReturn("");
        service.updateShortLink(1L, 1L, "https://example.com/new", null);
        assertEquals("https://example.com/new", service.findAvailableShortLink("AbC123").getOriginalUrl());
    }

    @Test
    void atomicRecordWithMissingExpiryMustFallBackToDatabase() {
        when(values.get("short-link:cache:v2:AbC123")).thenReturn("{\"version\":2,\"id\":1,\"shortCode\":\"AbC123\",\"originalUrl\":\"https://example.com/old\",\"status\":\"ACTIVE\"}");
        when(mapper.selectOne(any())).thenReturn(row("AbC123", "https://example.com/new"));
        assertEquals("https://example.com/new", service.findAvailableShortLink("AbC123").getOriginalUrl());
        verify(mapper).selectOne(any());
    }

    @Test
    void oldDatabaseReadCannotRefillCacheAfterCompletedEdit() throws Exception {
        ShortLink old = row("AbC123", "https://example.com/old");
        AtomicReference<ShortLink> database = new AtomicReference<>(old);
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch snapshotRead = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        CountDownLatch editingStarted = new CountDownLatch(1);
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            ShortLink snapshot = database.get();
            if (reads.incrementAndGet() == 1) {
                snapshotRead.countDown();
                if (!releaseRead.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("查询等待超时");
            }
            return snapshot;
        });
        when(mapper.selectByWorkspaceIdAndId(1L, 1L)).thenAnswer(invocation -> database.get());
        when(mapper.updateContentByWorkspaceIdAndId(eq(1L), eq(1L), anyString(), isNull()))
                .thenAnswer(invocation -> { database.set(row("AbC123", invocation.getArgument(2))); return 1; });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var reader = executor.submit(() -> service.findAvailableShortLink("AbC123"));
            assertTrue(snapshotRead.await(5, TimeUnit.SECONDS));
            var editor = executor.submit(() -> {
                editingStarted.countDown();
                return service.updateShortLink(1L, 1L, "https://example.com/new", null);
            });
            assertTrue(editingStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> editor.get(100, TimeUnit.MILLISECONDS));
            releaseRead.countDown();
            reader.get(5, TimeUnit.SECONDS);
            editor.get(5, TimeUnit.SECONDS);
            assertEquals("https://example.com/new", service.findAvailableShortLink("AbC123").getOriginalUrl());
        } finally {
            releaseRead.countDown();
            executor.shutdownNow();
        }
    }
}
