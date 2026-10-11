package com.lzq.shortlink.controller;

import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.exception.GlobalExceptionHandler;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import com.lzq.shortlink.message.VisitEventPublisher;
import com.lzq.shortlink.service.ShortLinkService;
import com.lzq.shortlink.service.impl.ShortLinkServiceImpl;
import com.lzq.shortlink.trace.RequestTraceFilter;
import com.lzq.shortlink.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真实控制器、校验器和业务服务验证 HTTP 流程，隔离数据库及消息副作用。 */
class TargetUrlValidationFlowTest {
    private ShortLinkMapper mapper;
    private StringRedisTemplate redis;
    private VisitEventPublisher publisher;
    private ShortLinkServiceImpl service;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mapper = mock(ShortLinkMapper.class);
        redis = mock(StringRedisTemplate.class);
        publisher = mock(VisitEventPublisher.class);
        service = new ShortLinkServiceImpl(redis, mapper, publisher);
        mvc = buildMvc(new WorkspaceLinkController(mock(WorkspaceAccessService.class), service));
    }

    private MockMvc buildMvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestTraceFilter()).build();
    }

    private String body(String url, boolean custom) {
        return json.writeValueAsString(custom
                ? Map.of("originalUrl", url, "shortCode", "urlTest1")
                : Map.of("originalUrl", url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "https://u:p@example.com", "https://example.com/%0d%0a", "https://example.com:65536"})
    void invalidCreateNeverWritesDatabaseOrCache(String url) throws Exception {
        for (boolean custom : new boolean[]{false, true}) {
            mvc.perform(post("/api/v1/workspaces/1/links")
                            .header("X-Request-Id", "url-test-create")
                            .contentType(MediaType.APPLICATION_JSON).content(body(url, custom)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TARGET_URL"))
                    .andExpect(jsonPath("$.traceId").value("url-test-create"))
                    .andExpect(header().string("X-Request-Id", "url-test-create"));
        }
        verifyNoInteractions(mapper, redis, publisher);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void validCreatePersistsNormalizedUrl(boolean custom) throws Exception {
        doAnswer(invocation -> {
            ShortLink link = invocation.getArgument(0);
            link.setId(100L);
            assertEquals("https://example.com/A%2fb?q=a+b&x=%26#X", link.getOriginalUrl());
            assertEquals(1L, link.getWorkspaceId());
            return 1;
        }).when(mapper).insert(any(ShortLink.class));
        mvc.perform(post("/api/v1/workspaces/1/links").contentType(MediaType.APPLICATION_JSON)
                        .content(body("HTTPS://EXAMPLE.COM/A%2fb?q=a+b&x=%26#X", custom)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/A%2fb?q=a+b&x=%26#X"));
        verify(mapper).insert(any(ShortLink.class));
        verifyNoInteractions(publisher);
    }

    private ShortLink existing(String url) {
        ShortLink link = new ShortLink();
        link.setId(100L);
        link.setWorkspaceId(1L);
        link.setShortCode("urlTest1");
        link.setOriginalUrl(url);
        link.setStatus("ACTIVE");
        link.setVisitCount(0L);
        return link;
    }

    @Test
    void invalidEditRetainsOldUrlAndDoesNotEvictCache() throws Exception {
        ShortLink old = existing("https://example.com/old");
        when(mapper.selectByWorkspaceIdAndId(1L, 100L)).thenReturn(old);
        mvc.perform(put("/api/v1/workspaces/1/links/100").contentType(MediaType.APPLICATION_JSON)
                        .content(body("javascript:alert(1)", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TARGET_URL"));
        verify(mapper, never()).updateContentByWorkspaceIdAndId(anyLong(), anyLong(), anyString(), any());
        assertEquals("https://example.com/old", old.getOriginalUrl());
        verifyNoInteractions(redis, publisher);
    }

    @Test
    void validEditNormalizesAndEvictsOldCache() throws Exception {
        ShortLink link = existing("https://example.com/old");
        when(mapper.selectByWorkspaceIdAndId(1L, 100L)).thenReturn(link);
        when(mapper.updateContentByWorkspaceIdAndId(eq(1L), eq(100L), anyString(), isNull()))
                .thenAnswer(invocation -> { link.setOriginalUrl(invocation.getArgument(2)); return 1; });
        mvc.perform(put("/api/v1/workspaces/1/links/100").contentType(MediaType.APPLICATION_JSON)
                        .content(body("HTTPS://EXAMPLE.COM/new?q=%26", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/new?q=%26"));
        verify(mapper).updateContentByWorkspaceIdAndId(1L, 100L, "https://example.com/new?q=%26", null);
        verify(redis, atLeastOnce()).delete("short-link:cache:v2:urlTest1");
        verifyNoInteractions(publisher);
    }

    @Test
    void missingOrEmptyUrlIsValidationError() throws Exception {
        for (String content : new String[]{"{}", "{\"originalUrl\":\"\"}", "{\"originalUrl\":null}"}) {
            mvc.perform(post("/api/v1/workspaces/1/links").contentType(MediaType.APPLICATION_JSON).content(content))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(mapper, redis, publisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "https://u:p@example.com", "https://example.com/%0a"})
    void malformedHistoricalUrlReturns404WithoutRecordingVisit(String url) throws Exception {
        ShortLinkService lookup = mock(ShortLinkService.class);
        when(lookup.findAvailableShortLink("urlTest1")).thenReturn(existing(url));
        buildMvc(new RedirectController(lookup)).perform(get("/urlTest1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"))
                .andExpect(header().doesNotExist("Location"));
        verify(lookup, never()).recordVisit(any());
    }

    @Test
    void validRedirectReturns302AndRecordsExactlyOnce() throws Exception {
        ShortLinkService lookup = mock(ShortLinkService.class);
        ShortLink link = existing("HTTPS://EXAMPLE.COM/A%2fb?q=a+b&x=%26#X");
        when(lookup.findAvailableShortLink("urlTest1")).thenReturn(link);
        buildMvc(new RedirectController(lookup)).perform(get("/urlTest1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/A%2fb?q=a+b&x=%26#X"));
        verify(lookup, times(1)).recordVisit(link);
    }

    @Test
    void missingLinkReturns404WithoutRecordingVisit() throws Exception {
        ShortLinkService lookup = mock(ShortLinkService.class);
        buildMvc(new RedirectController(lookup)).perform(get("/urlTest1"))
                .andExpect(status().isNotFound());
        verify(lookup, never()).recordVisit(any());
    }
}
