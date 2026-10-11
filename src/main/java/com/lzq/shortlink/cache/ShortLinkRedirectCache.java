package com.lzq.shortlink.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.Striped;
import com.lzq.shortlink.entity.ShortLink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/** 单实例跳转缓存：严格大小写、原子记录、有限有效期及事务失效协调。 */
@Slf4j
public final class ShortLinkRedirectCache {
    private static final String VALUE_PREFIX = "short-link:cache:v2:";
    private static final String MISSING_PREFIX = "short-link:cache-missing:v2:";
    private static final Duration POSITIVE_TTL = Duration.ofSeconds(30);
    private static final Duration NEGATIVE_TTL = Duration.ofSeconds(30);

    private record Entry(int version, Long id, String shortCode, String originalUrl,
                         String status, String expireAt) { }

    private final StringRedisTemplate redis;
    private final Cache<String, ShortLinkLocalCacheValue> positive;
    private final Cache<String, Boolean> negative;
    private final ObjectMapper json = new ObjectMapper();
    private final Striped<Lock> locks = Striped.lock(256);
    // 失败后绕过旧 Redis 值；到期时所有新版旧值的最长 TTL 已经过完。
    private final Cache<String, Boolean> unsafeRedis = Caffeine.newBuilder()
            .expireAfterWrite(POSITIVE_TTL.plusSeconds(1)).build();
    private final ConcurrentHashMap<String, Integer> pendingTransactions = new ConcurrentHashMap<>();

    public ShortLinkRedirectCache(StringRedisTemplate redis,
                                 Cache<String, ShortLinkLocalCacheValue> positive,
                                 Cache<String, Boolean> negative) {
        this.redis = redis;
        this.positive = positive;
        this.negative = negative;
    }

    /** 同一短码读取、回填和修改互斥；不同大小写不合并。 */
    public <T> T locked(String code, Supplier<T> action) {
        Lock lock = locks.get(code);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public <T> T mutate(String code, Supplier<T> action) {
        boolean transactional = TransactionSynchronizationManager.isActualTransactionActive();
        locked(code, () -> {
            if (transactional) {
                if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                    throw new IllegalStateException("缓存修改需要事务完成回调");
                }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        finishMutation(code);
                    }
                });
            }
            pendingTransactions.merge(code, 1, Integer::sum);
            evict(code);
            return null;
        });
        // 不持缓存锁等待数据库行锁；修改期间读请求仍可回源，但禁止回填。
        try {
            return action.get();
        } finally {
            if (!transactional) finishMutation(code);
        }
    }

    private void finishMutation(String code) {
        locked(code, () -> {
            evict(code);
            pendingTransactions.compute(code, (key, count) -> count == null || count <= 1 ? null : count - 1);
            return null;
        });
    }

    private boolean cacheAllowed(String code) {
        return !TransactionSynchronizationManager.isActualTransactionActive()
                && !pendingTransactions.containsKey(code);
    }

    public boolean missing(String code) {
        if (!cacheAllowed(code)) return false;
        if (Boolean.TRUE.equals(negative.getIfPresent(code))) return true;
        if (!redisAllowed(code)) return false;
        try {
            return "1".equals(redis.opsForValue().get(MISSING_PREFIX + code));
        } catch (DataAccessException exception) {
            redisFailed(code, exception);
            return false;
        }
    }

    public ShortLink get(String code) {
        if (!cacheAllowed(code)) return null;
        ShortLinkLocalCacheValue local = positive.getIfPresent(code);
        if (local != null) {
            if (code.equals(local.shortCode()) && !local.expiredAt(LocalDateTime.now())) return local.toShortLink();
            positive.invalidate(code);
        }
        if (!redisAllowed(code)) return null;
        try {
            String raw = redis.opsForValue().get(VALUE_PREFIX + code);
            if (raw == null) return null;
            Entry entry = json.readValue(raw, Entry.class);
            if (entry.version() != 2 || entry.id() == null || entry.id() <= 0
                    || !code.equals(entry.shortCode()) || entry.originalUrl() == null
                    || entry.originalUrl().isBlank() || entry.status() == null || entry.expireAt() == null) {
                evict(code);
                return null;
            }
            LocalDateTime expiry = entry.expireAt().isEmpty() ? null : LocalDateTime.parse(entry.expireAt());
            ShortLinkLocalCacheValue cached = new ShortLinkLocalCacheValue(entry.id(), entry.shortCode(),
                    entry.originalUrl(), entry.status(), expiry);
            if (cached.expiredAt(LocalDateTime.now())) {
                evict(code);
                return null;
            }
            positive.put(code, cached);
            return cached.toShortLink();
        } catch (DataAccessException exception) {
            redisFailed(code, exception);
        } catch (RuntimeException exception) {
            log.warn("跳转缓存内容损坏，回源数据库，shortCode={}", code);
            evict(code);
        }
        return null;
    }

    public void put(ShortLink row) {
        String code = row.getShortCode();
        if (!cacheAllowed(code)) return;
        Duration ttl = POSITIVE_TTL;
        if (row.getExpireAt() != null) {
            Duration remaining = Duration.between(LocalDateTime.now(), row.getExpireAt());
            if (remaining.isNegative() || remaining.isZero()) return;
            if (remaining.compareTo(ttl) < 0) ttl = remaining;
        }
        positive.put(code, ShortLinkLocalCacheValue.from(row));
        negative.invalidate(code);
        if (!redisAllowed(code)) return;
        try {
            Entry entry = new Entry(2, row.getId(), code, row.getOriginalUrl(), row.getStatus(),
                    row.getExpireAt() == null ? "" : row.getExpireAt().toString());
            redis.opsForValue().set(VALUE_PREFIX + code, json.writeValueAsString(entry), ttl);
            redis.delete(MISSING_PREFIX + code);
        } catch (DataAccessException exception) {
            redisFailed(code, exception);
        }
    }

    public void putMissing(String code) {
        if (!cacheAllowed(code)) return;
        positive.invalidate(code);
        negative.put(code, Boolean.TRUE);
        if (!redisAllowed(code)) return;
        try {
            redis.opsForValue().set(MISSING_PREFIX + code, "1", NEGATIVE_TTL);
        } catch (DataAccessException exception) {
            redisFailed(code, exception);
        }
    }

    public void evict(String code) {
        positive.invalidate(code);
        negative.invalidate(code);
        try {
            deleteRedis(code);
            unsafeRedis.invalidate(code);
        } catch (DataAccessException exception) {
            redisFailed(code, exception);
        }
    }

    private boolean redisAllowed(String code) {
        if (!Boolean.TRUE.equals(unsafeRedis.getIfPresent(code))) return true;
        try {
            deleteRedis(code);
            unsafeRedis.invalidate(code);
            return true;
        } catch (DataAccessException exception) {
            return false;
        }
    }

    private void deleteRedis(String code) {
        redis.delete(VALUE_PREFIX + code);
        redis.delete(MISSING_PREFIX + code);
    }

    private void redisFailed(String code, DataAccessException exception) {
        unsafeRedis.put(code, Boolean.TRUE);
        log.warn("跳转 Redis 缓存操作失败，暂时绕过该短码的 Redis 缓存，shortCode={}", code, exception);
    }
}
