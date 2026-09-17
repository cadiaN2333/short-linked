package com.lzq.shortlink.service;
import com.lzq.shortlink.service.impl.ShortLinkServiceImpl;
import com.lzq.shortlink.message.VisitEventPublisher;

import java.util.ArrayDeque;
import java.util.Queue;

import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import com.lzq.shortlink.workspace.Workspace;
import com.lzq.shortlink.workspace.WorkspaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class ShortLinkServiceTest {

    @Autowired
    private ShortLinkService shortLinkService;

    @Autowired
    private ShortLinkMapper shortLinkMapper;

    @Autowired
    private WorkspaceMapper workspaceMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long workspaceId;

    @BeforeEach
    void setUp() {
        Workspace workspace = new Workspace();
        workspace.setName("短链接服务测试工作空间");
        workspace.setStatus("ACTIVE");
        workspaceMapper.insert(workspace);
        workspaceId = workspace.getId();
    }

    @Test
    @Transactional
    void shouldCreateShortLink() {
        String originalUrl = "https://example.com/articles/123";

        ShortLink createdShortLink = shortLinkService.createShortLink(
                workspaceId,
                originalUrl,
                null
        );

        assertNotNull(createdShortLink.getId());
        assertNotNull(createdShortLink.getShortCode());
        assertEquals(8, createdShortLink.getShortCode().length());
        assertEquals(originalUrl, createdShortLink.getOriginalUrl());
        assertNotNull(createdShortLink.getManageToken());
        assertEquals(32, createdShortLink.getManageToken().length());

        ShortLink savedShortLink = shortLinkMapper.selectById(createdShortLink.getId());

        assertNotNull(savedShortLink);
        assertEquals(createdShortLink.getShortCode(), savedShortLink.getShortCode());
    }

    @Test
    @Transactional
    void shouldFindStatisticsOnlyWhenManageTokenMatches() {
        ShortLink createdShortLink = shortLinkService.createShortLink(
                workspaceId,
                "https://example.com/statistics",
                null
        );

        ShortLink matchedShortLink = shortLinkService.findShortLinkForStatistics(
                createdShortLink.getShortCode(),
                createdShortLink.getManageToken()
        );

        assertNotNull(matchedShortLink);
        assertEquals(createdShortLink.getId(), matchedShortLink.getId());

        ShortLink unmatchedShortLink = shortLinkService.findShortLinkForStatistics(
                createdShortLink.getShortCode(),
                "invalid-token"
        );

        assertNull(unmatchedShortLink);
    }

    @Test
    @Transactional
    void shouldRetryWhenShortCodeAlreadyExists() {
        String conflictedCode = "abc12345";
        String availableCode = "def67890";

        ShortLink existingShortLink = new ShortLink();
        existingShortLink.setWorkspaceId(workspaceId);
        existingShortLink.setShortCode(conflictedCode);
        existingShortLink.setOriginalUrl("https://example.com/existing");
        existingShortLink.setManageToken("a".repeat(32));

        shortLinkMapper.insert(existingShortLink);

        ShortLinkService fixedShortCodeService = new FixedShortCodeService(
                shortLinkMapper,
                stringRedisTemplate,
                conflictedCode,
                availableCode
        );

        ShortLink createdShortLink = fixedShortCodeService.createShortLink(
                workspaceId,
                "https://example.com/new-link",
                null
        );

        assertEquals(availableCode, createdShortLink.getShortCode());
        assertNotNull(createdShortLink.getId());

    }
    private static class FixedShortCodeService extends ShortLinkServiceImpl {

        private final Queue<String> shortCodes = new ArrayDeque<>();

        private FixedShortCodeService(
                ShortLinkMapper shortLinkMapper,
                StringRedisTemplate stringRedisTemplate,
                String... shortCodes
        ) {
            super(
                    stringRedisTemplate,
                    shortLinkMapper,
                    org.mockito.Mockito.mock(VisitEventPublisher.class)
            );
            this.shortCodes.addAll(java.util.List.of(shortCodes));
        }

        @Override
        protected String generateShortCode() {
            return shortCodes.remove();
        }
    }
}
