package com.lzq.shortlink.controller;

import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.service.ShortLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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

    @Test
    @Transactional
    void shouldCreateShortLink() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "https://example.com/articles/123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.originalUrl")
                        .value("https://example.com/articles/123"));
    }

    @Test
    @Transactional
    void shouldReturnStatisticsOnlyWhenManageTokenMatches() throws Exception {
        ShortLink shortLink = shortLinkService.createShortLink(
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
}
