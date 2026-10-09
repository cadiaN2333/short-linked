package com.lzq.shortlink.exception;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 全局异常响应追踪 ID 测试。 */
class GlobalExceptionHandlerTraceTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @BeforeEach
    void setTraceId() {
        MDC.put("traceId", "request-test-123");
    }

    @AfterEach
    void clearTraceId() {
        MDC.remove("traceId");
    }

    @Test
    void shouldIncludeTraceIdInRequestBodyErrors() {
        var response = handler.handleRequestBodyException(
                new HttpMessageNotReadableException(
                        "invalid json",
                        new IllegalArgumentException("invalid json"),
                        new MockHttpInputMessage(new byte[0])
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("request-test-123", response.getBody().getTraceId());
    }

    @Test
    void shouldHideUnexpectedExceptionDetailsFromClient() {
        var response = handler.handleUnexpectedException(
                new IllegalStateException("database password must not leak")
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().getCode());
        assertEquals("request-test-123", response.getBody().getTraceId());
        assertFalse(response.getBody().getMessage()
                .contains("database password"));
    }
}
