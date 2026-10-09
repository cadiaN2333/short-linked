package com.lzq.shortlink.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/** 为每个 HTTP 请求建立安全的追踪 ID，并绑定到日志上下文。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_TRACE_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = resolveTraceId(
                request.getHeader(RequestTraceContext.HEADER_NAME)
        );
        String previousTraceId = MDC.get(RequestTraceContext.MDC_KEY);

        response.setHeader(RequestTraceContext.HEADER_NAME, traceId);
        MDC.put(RequestTraceContext.MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousTraceId == null) {
                MDC.remove(RequestTraceContext.MDC_KEY);
            } else {
                MDC.put(RequestTraceContext.MDC_KEY, previousTraceId);
            }
        }
    }

    private String resolveTraceId(String incomingTraceId) {
        if (incomingTraceId != null
                && SAFE_TRACE_ID.matcher(incomingTraceId).matches()) {
            return incomingTraceId;
        }
        return UUID.randomUUID().toString();
    }
}
