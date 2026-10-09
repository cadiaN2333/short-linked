package com.lzq.shortlink.trace;

import org.slf4j.MDC;

/** 当前 HTTP 请求的追踪 ID 上下文。 */
public final class RequestTraceContext {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "traceId";

    private RequestTraceContext() {
    }

    /** 读取当前线程绑定的请求 ID。 */
    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }
}
