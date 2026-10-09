package com.lzq.shortlink.auth;

import com.lzq.shortlink.auth.exception.LoginAttemptRateLimitedException;
import com.lzq.shortlink.auth.exception.LoginRateLimiterUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** 使用 Redis 为登录失败次数提供跨实例限流。 */
@Slf4j
@Component
public class LoginAttemptRateLimiter {

    private static final String ACCOUNT_FAILURE_KEY_PREFIX =
            "auth:login:failure:account:";

    private static final String SOURCE_FAILURE_KEY_PREFIX =
            "auth:login:failure:source:";

    /** 一次 Lua 操作同时递增账号和来源计数，并在首次失败时设置窗口过期时间。 */
    private static final RedisScript<Long> RECORD_FAILURE_SCRIPT =
            new DefaultRedisScript<>("""
                    local account_count = redis.call('INCR', KEYS[1])
                    if account_count == 1 then
                        redis.call('EXPIRE', KEYS[1], ARGV[1])
                    end
                    local source_count = redis.call('INCR', KEYS[2])
                    if source_count == 1 then
                        redis.call('EXPIRE', KEYS[2], ARGV[1])
                    end
                    return 1
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final LoginRateLimitProperties properties;

    public LoginAttemptRateLimiter(
            StringRedisTemplate redisTemplate,
            LoginRateLimitProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        if (properties.getAccountFailureLimit() < 1
                || properties.getSourceFailureLimit() < 1
                || properties.getFailureWindow() == null
                || properties.getFailureWindow().isZero()
                || properties.getFailureWindow().isNegative()
                || properties.getFailureWindow().toSeconds() < 1) {
            throw new IllegalArgumentException("登录限流配置必须为正数");
        }
    }

    /** 在校验账号密码前检查账号和来源是否已触发临时限制。 */
    public void checkAllowed(String email, String sourceAddress) {
        String accountKey = accountFailureKey(email);
        String sourceKey = sourceFailureKey(sourceAddress);
        try {
            long accountFailures = readCount(accountKey);
            long sourceFailures = readCount(sourceKey);
            if (accountFailures >= properties.getAccountFailureLimit()
                    || sourceFailures >= properties.getSourceFailureLimit()) {
                long retryAfter = 0;
                if (accountFailures >= properties.getAccountFailureLimit()) {
                    retryAfter = Math.max(retryAfter, remainingSeconds(accountKey));
                }
                if (sourceFailures >= properties.getSourceFailureLimit()) {
                    retryAfter = Math.max(retryAfter, remainingSeconds(sourceKey));
                }
                throw new LoginAttemptRateLimitedException(
                        Math.max(1, retryAfter)
                );
            }
        } catch (DataAccessException exception) {
            log.error("登录限流存储不可用，拒绝本次登录校验");
            throw new LoginRateLimiterUnavailableException();
        }
    }

    /** 记录一次账号和来源的认证失败。 */
    public void recordFailure(String email, String sourceAddress) {
        String accountKey = accountFailureKey(email);
        String sourceKey = sourceFailureKey(sourceAddress);
        try {
            redisTemplate.execute(
                    RECORD_FAILURE_SCRIPT,
                    List.of(accountKey, sourceKey),
                    String.valueOf(properties.getFailureWindow().toSeconds())
            );
        } catch (DataAccessException exception) {
            log.error("登录失败次数写入 Redis 失败");
            throw new LoginRateLimiterUnavailableException();
        }
    }

    /** 成功登录后清除该账号的失败计数，来源计数保留以限制密码喷洒。 */
    public void clearAccountFailures(String email) {
        try {
            redisTemplate.delete(accountFailureKey(email));
        } catch (DataAccessException exception) {
            log.error("登录成功后清理账号失败计数失败");
            throw new LoginRateLimiterUnavailableException();
        }
    }

    private long readCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            log.error("登录失败计数格式异常，拒绝登录校验");
            throw new LoginRateLimiterUnavailableException();
        }
    }

    private long remainingSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl < 1) {
            return Math.max(1, properties.getFailureWindow().toSeconds());
        }
        return ttl;
    }

    private String accountFailureKey(String email) {
        return ACCOUNT_FAILURE_KEY_PREFIX + sha256(normalizeEmail(email));
    }

    private String sourceFailureKey(String sourceAddress) {
        String normalized = sourceAddress == null || sourceAddress.isBlank()
                ? "unknown"
                : sourceAddress.trim();
        return SOURCE_FAILURE_KEY_PREFIX + sha256(normalized);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Redis Key 只保存摘要，避免把邮箱和 IP 明文写入键名。 */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
