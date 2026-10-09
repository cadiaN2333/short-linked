package com.lzq.shortlink.controller;

import com.lzq.shortlink.auth.AuthService;
import com.lzq.shortlink.auth.dto.AuthTokenResponse;
import com.lzq.shortlink.auth.dto.LoginRequest;
import com.lzq.shortlink.auth.dto.RegisterRequest;
import com.lzq.shortlink.entity.AppUser;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.service.ShortLinkService;
import com.lzq.shortlink.mapper.ShortLinkDailyStatMapper;
import java.time.LocalDate;
import com.lzq.shortlink.workspace.Workspace;
import com.lzq.shortlink.workspace.WorkspaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 工作空间短链接接口真实上下文联调测试。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkspaceLinkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private WorkspaceMapper workspaceMapper;

    @Autowired
    private ShortLinkService shortLinkService;

    @Autowired
    private ShortLinkDailyStatMapper shortLinkDailyStatMapper;

    private String accessToken;

    private Long workspaceId;

    @BeforeEach
    void setUp() {
        String email = "workspace-"
                + UUID.randomUUID()
                + "@example.com";

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(email);
        registerRequest.setPassword("Demo@123456");
        AppUser user = authService.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("Demo@123456");
        AuthTokenResponse tokenResponse = authService.login(loginRequest);
        accessToken = tokenResponse.getAccessToken();

        Workspace workspace = workspaceMapper.selectActiveByUserId(
                user.getId()
        ).get(0);
        workspaceId = workspace.getId();
    }

    @Test
    void shouldListCurrentUserWorkspaces() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(workspaceId));
    }

    @Test
    void shouldReturnUnifiedErrorWithTraceIdForUnauthenticatedApi()
            throws Exception {
        mockMvc.perform(get("/api/v1/workspaces")
                        .header("X-Request-Id", "unauthenticated-req-01"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        "X-Request-Id",
                        "unauthenticated-req-01"
                ))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId")
                        .value("unauthenticated-req-01"));
    }

    @Test
    void shouldReturnUnifiedErrorForInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer invalid-token")
                        .header("X-Request-Id", "invalid-token-req-01"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        "WWW-Authenticate",
                        org.hamcrest.Matchers.startsWith("Bearer")
                ))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId")
                        .value("invalid-token-req-01"));
    }

    @Test
    void shouldExposeOnlyPublicHealthStatus() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("X-Request-Id", "health-check-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(header().string("X-Request-Id", "health-check-01"));

        mockMvc.perform(get("/actuator/env")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnUnifiedErrorForMissingApiRoute() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-resource")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Request-Id", "missing-route-01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("missing-route-01"));
    }

    @Test
    void shouldReturnUnifiedErrorForUnsupportedHttpMethod() throws Exception {
        mockMvc.perform(delete("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Request-Id", "method-not-allowed-01"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(
                        "Allow",
                        org.hamcrest.Matchers.containsString("GET")
                ))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.traceId")
                        .value("method-not-allowed-01"));
    }

    @Test
    void shouldAddMemberAndEnforceMemberReadOnlyPermissions()
            throws Exception {
        String memberEmail = "member-"
                + UUID.randomUUID()
                + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(memberEmail);
        registerRequest.setPassword("Demo@123456");
        authService.register(registerRequest);

        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/members",
                        workspaceId
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "role": "MEMBER"
                                }
                                """.formatted(memberEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(memberEmail))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        LoginRequest memberLogin = new LoginRequest();
        memberLogin.setEmail(memberEmail);
        memberLogin.setPassword("Demo@123456");
        String memberToken = authService.login(memberLogin).getAccessToken();

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/members",
                        workspaceId
                )
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/links",
                        workspaceId
                )
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());

        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/links",
                        workspaceId
                )
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://example.com/member-write"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    void shouldCreateShortLinkWithWorkspaceScope() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/links",
                        workspaceId
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/workspace"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.shortCode").isNotEmpty());
    }

    @Test
    void shouldReturnNotFoundForLegacyStatisticsEndpoint() throws Exception {
        ShortLink shortLink = shortLinkService.createShortLink(
                workspaceId,
                "https://example.com/legacy-statistics",
                null
        );

        mockMvc.perform(get(
                        "/api/links/{shortCode}/stats",
                        shortLink.getShortCode()
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Manage-Token", "legacy-credential"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/links")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalUrl":"https://example.com/legacy-create"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectUnsupportedAnalyticsGranularity() throws Exception {
        ShortLink shortLink = shortLinkService.createShortLink(
                workspaceId,
                "https://example.com/analytics-granularity",
                null
        );

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/links/{linkId}/analytics",
                        workspaceId,
                        shortLink.getId()
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("granularity", "hour"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_STATISTICS_RANGE"));
    }

    @Test
    void shouldRejectAnalyticsRangeLongerThanNinetyDays() throws Exception {
        ShortLink shortLink = shortLinkService.createShortLink(
                workspaceId,
                "https://example.com/analytics-range",
                null
        );

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/links/{linkId}/analytics",
                        workspaceId,
                        shortLink.getId()
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("from", "2026-01-01")
                        .queryParam("to", "2026-04-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_STATISTICS_RANGE"));
    }

    @Test
    void shouldRejectWorkspaceThatCurrentUserCannotAccess() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/links",
                        999999999L
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/denied"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    void shouldListShortLinksOnlyInsideCurrentWorkspace() throws Exception {
        ShortLink shortLink = shortLinkService.createShortLink(
                workspaceId,
                "https://example.com/list",
                null
        );

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/links",
                        workspaceId
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("page", "1")
                        .queryParam("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id")
                        .value(shortLink.getId()))
                .andExpect(jsonPath("$.items[0].workspaceId")
                        .value(workspaceId))
                .andExpect(jsonPath("$.items[0].manageToken").doesNotExist());
    }

    @Test
    void shouldGetShortLinkDetailInsideCurrentWorkspace() throws Exception {
        ShortLink shortLink = shortLinkService.createShortLink(
                workspaceId,
                "https://example.com/detail",
                null
        );

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/links/{linkId}",
                        workspaceId,
                        shortLink.getId()
                )
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shortLink.getId()))
                .andExpect(jsonPath("$.originalUrl")
                        .value("https://example.com/detail"))
                .andExpect(jsonPath("$.manageToken").doesNotExist());
    }

    @Test
    void shouldUpdateDisableAndSoftDeleteShortLink() throws Exception {
        ShortLink shortLink = shortLinkService.createShortLink(
                workspaceId,
                "https://example.com/lifecycle-old",
                null
        );

        mockMvc.perform(put(
                        "/api/v1/workspaces/{workspaceId}/links/{linkId}",
                        workspaceId,
                        shortLink.getId()
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/lifecycle-new"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl")
                        .value("https://example.com/lifecycle-new"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(patch(
                        "/api/v1/workspaces/{workspaceId}/links/{linkId}/status",
                        workspaceId,
                        shortLink.getId()
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DISABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(get("/{shortCode}", shortLink.getShortCode()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(
                        "/api/v1/workspaces/{workspaceId}/links/{linkId}",
                        workspaceId,
                        shortLink.getId()
                )
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/links/{linkId}",
                        workspaceId,
                        shortLink.getId()
                )
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
    }

    @Test
    void shouldQueryAnalyticsInsideCurrentWorkspace() throws Exception {
        ShortLink shortLink = shortLinkService.createShortLink(
                workspaceId,
                "https://example.com/analytics",
                null
        );
        LocalDate today = LocalDate.now();
        shortLinkDailyStatMapper.incrementPv(shortLink.getId(), today);
        shortLinkDailyStatMapper.incrementPv(shortLink.getId(), today);

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/links/{linkId}/analytics",
                        workspaceId,
                        shortLink.getId()
                )
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode")
                        .value(shortLink.getShortCode()))
                .andExpect(jsonPath("$.granularity").value("day"))
                .andExpect(jsonPath("$.pvTrend.length()").value(7))
                .andExpect(jsonPath("$.pvTrend[6].date")
                        .value(today.toString()))
                .andExpect(jsonPath("$.pvTrend[6].pv").value(2));
    }

    @Test
    void shouldFilterShortLinksByStatusAndKeyword() throws Exception {
        ShortLink activeLink = shortLinkService.createShortLink(
                workspaceId,
                "https://example.com/filter-active",
                null
        );
        ShortLink disabledLink = shortLinkService.createShortLink(
                workspaceId,
                "https://example.com/filter-disabled",
                null
        );

        mockMvc.perform(patch(
                        "/api/v1/workspaces/{workspaceId}/links/{linkId}/status",
                        workspaceId,
                        disabledLink.getId()
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DISABLED"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/links",
                        workspaceId
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .queryParam("status", "DISABLED")
                        .queryParam("keyword", "filter-disabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id")
                        .value(disabledLink.getId()))
                .andExpect(jsonPath("$.items[0].status")
                        .value("DISABLED"));
    }

    @Test
    void shouldCreateCustomShortCodeAndRejectDuplicateOrReservedCode()
            throws Exception {
        String customCode = "promo"
                + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8);
        String requestBody = """
                {
                  "originalUrl": "https://example.com/custom-code",
                  "shortCode": "%s"
                }
                """.formatted(customCode);

        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/links",
                        workspaceId
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value(customCode));

        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/links",
                        workspaceId
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SHORT_CODE_ALREADY_EXISTS"));

        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/links",
                        workspaceId
                )
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/reserved",
                                  "shortCode": "api"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("RESERVED_SHORT_CODE"));
    }
}
