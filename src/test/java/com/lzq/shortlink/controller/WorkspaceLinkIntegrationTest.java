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
