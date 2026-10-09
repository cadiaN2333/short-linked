package com.lzq.shortlink.trace;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 请求追踪 ID 过滤器测试。 */
class RequestTraceFilterTest {

    private final RequestTraceFilter filter = new RequestTraceFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldReuseValidRequestIdAndClearMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceContext.HEADER_NAME, "req-abc_123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcTraceId = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                mdcTraceId.set(MDC.get(RequestTraceContext.MDC_KEY))
        );

        assertEquals("req-abc_123", mdcTraceId.get());
        assertEquals(
                "req-abc_123",
                response.getHeader(RequestTraceContext.HEADER_NAME)
        );
        assertNull(MDC.get(RequestTraceContext.MDC_KEY));
    }

    @Test
    void shouldGenerateUuidWhenRequestIdIsMissingOrInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceContext.HEADER_NAME, "bad request id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        String traceId = response.getHeader(RequestTraceContext.HEADER_NAME);
        assertNotEquals("bad request id", traceId);
        assertEquals(UUID.fromString(traceId).toString(), traceId);
    }

    @Test
    void shouldGenerateUuidWhenRequestIdIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        String traceId = response.getHeader(RequestTraceContext.HEADER_NAME);
        assertEquals(UUID.fromString(traceId).toString(), traceId);
    }

    @Test
    void shouldClearMdcWhenRequestChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
                ServletException.class,
                () -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                    assertTrue(MDC.get(RequestTraceContext.MDC_KEY) != null);
                    throw new ServletException("测试异常");
                })
        );

        assertNull(MDC.get(RequestTraceContext.MDC_KEY));
        assertTrue(response.getHeader(RequestTraceContext.HEADER_NAME) != null);
    }
}
