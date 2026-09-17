package com.lzq.shortlink.controller;

import com.lzq.shortlink.auth.AuthService;
import com.lzq.shortlink.auth.dto.AuthTokenResponse;
import com.lzq.shortlink.auth.dto.LoginRequest;
import com.lzq.shortlink.auth.dto.RegisterRequest;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.mapper.ShortLinkDailyStatMapper;
import com.lzq.shortlink.service.ShortLinkService;
import com.lzq.shortlink.workspace.Workspace;
import com.lzq.shortlink.workspace.WorkspaceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 短链接接口测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShortLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortLinkService shortLinkService;

    @Autowired
    private ShortLinkDailyStatMapper shortLinkDailyStatMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private WorkspaceMapper workspaceMapper;

    @Test
    @Transactional
    void shouldRejectLegacyCreateEndpoint() throws Exception {
        String email = "legacy-" + UUID.randomUUID() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(email);
        registerRequest.setPassword("Demo@123456");
        authService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("Demo@123456");
        AuthTokenResponse token = authService.login(loginRequest);

        mockMvc.perform(post("/api/links")
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/articles/123"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void shouldReturnStatisticsOnlyWhenManageTokenMatches() throws Exception {
        Long workspaceId = createTestWorkspace();
        ShortLink shortLink = shortLinkService.createShortLink(
                workspaceId,
                "https://example.com/statistics",
                null
        );

        mockMvc.perform(get("/api/links/{shortCode}/stats", shortLink.getShortCode())
                        .header("X-Manage-Token", shortLink.getManageToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value(shortLink.getShortCode()))
                .andExpect(jsonPath("$.originalUrl")
                        .value("https://example.com/statistics"));

        mockMvc.perform(get("/api/links/{shortCode}/stats", shortLink.getShortCode())
                        .header("X-Manage-Token", "invalid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
    }

    @Test
    @Transactional
    void shouldReturnSevenDayPvTrendByDefault() throws Exception {
        Long workspaceId = createTestWorkspace();
        ShortLink shortLink = shortLinkService.createShortLink(
                workspaceId,
                "https://example.com/trend",
                null
        );

        mockMvc.perform(get(
                        "/api/links/{shortCode}/stats",
                        shortLink.getShortCode()
                )
                        .header("X-Manage-Token", shortLink.getManageToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("day"))
                .andExpect(jsonPath("$.pvTrend.length()").value(7))
                .andExpect(jsonPath("$.pvTrend[0].pv").value(0));
    }

    @Test
    void shouldRejectUnsupportedGranularity() throws Exception {
        mockMvc.perform(get("/api/links/abc12345/stats")
                        .queryParam("granularity", "hour")
                        .header("X-Manage-Token", "invalid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_STATISTICS_RANGE"));
    }

    @Test
    void shouldRejectMoreThanNinetyDays() throws Exception {
        mockMvc.perform(get("/api/links/abc12345/stats")
                        .queryParam("from", "2026-01-01")
                        .queryParam("to", "2026-04-01")
                        .header("X-Manage-Token", "invalid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_STATISTICS_RANGE"));
    }

    @Test
    @Transactional
    void shouldReturnPvForRequestedDateRange() throws Exception {
        Long workspaceId = createTestWorkspace();
        ShortLink shortLink = shortLinkService.createShortLink(
                workspaceId,
                "https://example.com/trend-with-data",
                null
        );
        LocalDate statDate = LocalDate.of(2026, 9, 15);
        shortLinkDailyStatMapper.incrementPv(shortLink.getId(), statDate);
        shortLinkDailyStatMapper.incrementPv(shortLink.getId(), statDate);

        mockMvc.perform(get(
                        "/api/links/{shortCode}/stats",
                        shortLink.getShortCode()
                )
                        .queryParam("from", "2026-09-14")
                        .queryParam("to", "2026-09-16")
                        .header("X-Manage-Token", shortLink.getManageToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pvTrend.length()").value(3))
                .andExpect(jsonPath("$.pvTrend[0].date")
                        .value("2026-09-14"))
                .andExpect(jsonPath("$.pvTrend[0].pv").value(0))
                .andExpect(jsonPath("$.pvTrend[1].date")
                        .value("2026-09-15"))
                .andExpect(jsonPath("$.pvTrend[1].pv").value(2))
                .andExpect(jsonPath("$.pvTrend[2].date")
                        .value("2026-09-16"))
                .andExpect(jsonPath("$.pvTrend[2].pv").value(0));
    }

    private Long createTestWorkspace() {
        Workspace workspace = new Workspace();
        workspace.setName("兼容统计测试工作空间");
        workspace.setStatus("ACTIVE");
        workspaceMapper.insert(workspace);
        return workspace.getId();
    }
}
