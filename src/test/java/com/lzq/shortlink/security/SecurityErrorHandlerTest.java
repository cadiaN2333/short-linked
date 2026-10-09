package com.lzq.shortlink.security;

import com.lzq.shortlink.trace.RequestTraceContext;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spring Security 错误处理器序列化测试。 */
@SpringBootTest
class SecurityErrorHandlerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldWriteUnifiedUnauthorizedJson() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(RequestTraceContext.MDC_KEY, "security-401-01");
        AuthenticationException exception =
                new BadCredentialsException("secret must not be returned");

        new JsonAuthenticationEntryPoint(objectMapper)
                .commence(request, response, exception);

        String body = response.getContentAsString();
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getHeader("WWW-Authenticate").startsWith("Bearer"));
        assertTrue(body.contains("UNAUTHORIZED"));
        assertTrue(body.contains("security-401-01"));
        assertFalse(body.contains("secret must not be returned"));
    }

    @Test
    void shouldWriteUnifiedForbiddenJson() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(RequestTraceContext.MDC_KEY, "security-403-01");

        new JsonAccessDeniedHandler(objectMapper).handle(
                request,
                response,
                new AccessDeniedException("secret must not be returned")
        );

        String body = response.getContentAsString();
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(body.contains("FORBIDDEN"));
        assertTrue(body.contains("security-403-01"));
        assertFalse(body.contains("secret must not be returned"));
    }
}
