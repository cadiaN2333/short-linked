package com.lzq.shortlink.exception;

import com.lzq.shortlink.auth.exception.LoginAttemptRateLimitedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginRateLimitExceptionHandlerTest {

    @Test
    void shouldReturn429AndRetryAfterForBlockedLogin() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleLoginRateLimited(
                new LoginAttemptRateLimitedException(73)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("73", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("LOGIN_RATE_LIMITED", response.getBody().getCode());
    }
}
