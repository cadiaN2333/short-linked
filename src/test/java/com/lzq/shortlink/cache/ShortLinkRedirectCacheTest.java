package com.lzq.shortlink.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.lzq.shortlink.entity.ShortLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 缓存恢复和事务完成事件使用真实组件及真实 Spring 事务回调机制。 */
class ShortLinkRedirectCacheTest {
    private ShortLinkRedirectCache cache;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private final String code = "AbC123";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        cache = new ShortLinkRedirectCache(redis, Caffeine.newBuilder().build(), Caffeine.newBuilder().build());
    }

    private ShortLink row(String shortCode, String url) {
        ShortLink row = new ShortLink();
        row.setId(1L);
        row.setShortCode(shortCode);
        row.setOriginalUrl(url);
        row.setStatus("ACTIVE");
        return row;
    }

    @Test
    void cacheKeysAndLocalValuesRemainCaseSensitive() {
        cache.put(row("AbC123", "https://example.com/upper"));
        cache.put(row("abc123", "https://example.com/lower"));
        assertEquals("https://example.com/upper", cache.get("AbC123").getOriginalUrl());
        assertEquals("https://example.com/lower", cache.get("abc123").getOriginalUrl());
        cache.evict("AbC123");
        assertNull(cache.get("AbC123"));
        assertNotNull(cache.get("abc123"));
    }

    @Test
    void recoveryDeletesStaleRedisBeforeReadingIt() {
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("模拟清理失败"));
        cache.evict(code);
        clearInvocations(values);
        assertNull(cache.get(code));
        verify(values, never()).get(anyString());
        doReturn(true).when(redis).delete(anyString());
        cache.get(code);
        var order = inOrder(redis, values);
        order.verify(redis, atLeastOnce()).delete("short-link:cache:v2:" + code);
        order.verify(redis).delete("short-link:cache-missing:v2:" + code);
        order.verify(values).get("short-link:cache:v2:" + code);
    }

    @Test
    void expiredAtomicRecordCannotBeReturned() {
        when(values.get("short-link:cache:v2:" + code)).thenReturn(
                "{\"version\":2,\"id\":1,\"shortCode\":\"AbC123\",\"originalUrl\":\"https://example.com\",\"status\":\"ACTIVE\",\"expireAt\":\"2000-01-01T00:00:00\"}");
        assertNull(cache.get(code));
        verify(redis).delete("short-link:cache:v2:" + code);
    }

    @Test
    void mismatchedStoredCodeCannotCreateAnAlias() {
        when(values.get("short-link:cache:v2:" + code)).thenReturn(
                "{\"version\":2,\"id\":1,\"shortCode\":\"abc123\",\"originalUrl\":\"https://example.com\",\"status\":\"ACTIVE\",\"expireAt\":\"\"}");
        assertNull(cache.get(code));
    }

    @Test
    void temporaryLinksCannotCacheBeyondTheirExpiry() {
        ShortLink row = row(code, "https://example.com");
        row.setExpireAt(LocalDateTime.now().plusSeconds(10));
        cache.put(row);
        verify(values).set(anyString(), anyString(), argThat((java.time.Duration ttl) ->
                ttl.compareTo(java.time.Duration.ZERO) > 0 && ttl.compareTo(java.time.Duration.ofSeconds(10)) <= 0));
    }

    @Test
    void rollbackDoesNotPublishUncommittedValuesAndReleasesPendingState() {
        cache.put(row(code, "https://example.com/old"));
        new TransactionTemplate(new LocalTransactionManager()).execute(status -> {
            cache.mutate(code, () -> {
                cache.put(row(code, "https://example.com/uncommitted"));
                assertNull(cache.get(code));
                return null;
            });
            status.setRollbackOnly();
            return null;
        });
        assertNull(cache.get(code));
        cache.put(row(code, "https://example.com/old"));
        assertEquals("https://example.com/old", cache.get(code).getOriginalUrl());
    }

    @Test
    void otherThreadsCannotRefillCacheWhileMutationTransactionIsPending() throws Exception {
        CountDownLatch pending = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var transaction = executor.submit(() -> new TransactionTemplate(new LocalTransactionManager()).execute(status -> {
                cache.mutate(code, () -> null);
                pending.countDown();
                try {
                    if (!finish.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("等待提交超时");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return null;
            }));
            assertTrue(pending.await(5, TimeUnit.SECONDS));
            cache.locked(code, () -> { cache.put(row(code, "https://example.com/old")); return null; });
            assertNull(cache.locked(code, () -> cache.get(code)));
            finish.countDown();
            transaction.get(5, TimeUnit.SECONDS);
            cache.put(row(code, "https://example.com/new"));
            assertEquals("https://example.com/new", cache.get(code).getOriginalUrl());
        } finally {
            finish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void databaseWaiterMustNotBlockOwnersNextMutationOrCommit() throws Exception {
        // 模拟数据库行锁由事务一直持有，其他写入必须等到提交。
        ReentrantLock rowLock = new ReentrantLock();
        CountDownLatch firstWrite = new CountDownLatch(1);
        CountDownLatch competitorWaiting = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        Future<?> competitor = null;
        try {
            var owner = executor.submit(() -> new TransactionTemplate(new LocalTransactionManager() {
                @Override protected void doCommit(DefaultTransactionStatus status) { rowLock.unlock(); }
            }).execute(status -> {
                cache.mutate(code, () -> { rowLock.lock(); return null; });
                firstWrite.countDown();
                try {
                    if (!competitorWaiting.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("未进入竞争写入");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                cache.mutate(code, () -> null);
                return null;
            }));
            assertTrue(firstWrite.await(5, TimeUnit.SECONDS));
            competitor = executor.submit(() -> cache.mutate(code, () -> {
                competitorWaiting.countDown();
                try {
                    rowLock.lockInterruptibly();
                    rowLock.unlock();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            owner.get(2, TimeUnit.SECONDS);
            competitor.get(2, TimeUnit.SECONDS);
        } finally {
            if (competitor != null) competitor.cancel(true);
            executor.shutdownNow();
        }
    }

    /** 无外部资源的事务管理器，仅用于驱动真实同步回调。 */
    private static class LocalTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
