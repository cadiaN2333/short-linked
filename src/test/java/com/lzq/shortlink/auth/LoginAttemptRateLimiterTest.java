package com.lzq.shortlink.auth;

import com.lzq.shortlink.auth.exception.LoginAttemptRateLimitedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginAttemptRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new LoginAttemptRateLimiter(
                redisTemplate,
                new LoginRateLimitProperties()
        );
    }

    @Test
    void shouldBlockAccountAfterFiveFailedAttempts() {
        when(valueOperations.get(anyString()))
                .thenReturn("5")
                .thenReturn("2");
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS)))
                .thenReturn(73L);

        LoginAttemptRateLimitedException exception = assertThrows(
                LoginAttemptRateLimitedException.class,
                () -> rateLimiter.checkAllowed(
                        " Demo@Example.com ",
                        "192.0.2.10"
                )
        );

        assertEquals(73L, exception.getRetryAfterSeconds());
    }

    @Test
    void shouldBlockSourceAfterTwentyFailedAttempts() {
        when(valueOperations.get(anyString()))
                .thenReturn("1")
                .thenReturn("20");
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS)))
                .thenReturn(91L);

        LoginAttemptRateLimitedException exception = assertThrows(
                LoginAttemptRateLimitedException.class,
                () -> rateLimiter.checkAllowed(
                        "demo@example.com",
                        "192.0.2.11"
                )
        );

        assertEquals(91L, exception.getRetryAfterSeconds());
    }
}
