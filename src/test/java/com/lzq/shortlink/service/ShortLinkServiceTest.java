package com.lzq.shortlink.service;
import com.lzq.shortlink.service.impl.ShortLinkServiceImpl;

import java.util.ArrayDeque;
import java.util.Queue;

import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class ShortLinkServiceTest {

    @Autowired
    private ShortLinkService shortLinkService;

    @Autowired
    private ShortLinkMapper shortLinkMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @Transactional
    void shouldCreateShortLink() {
        String originalUrl = "https://example.com/articles/123";

        ShortLink createdShortLink = shortLinkService.createShortLink(originalUrl, null);

        assertNotNull(createdShortLink.getId());
        assertNotNull(createdShortLink.getShortCode());
        assertEquals(8, createdShortLink.getShortCode().length());
        assertEquals(originalUrl, createdShortLink.getOriginalUrl());

        ShortLink savedShortLink = shortLinkMapper.selectById(createdShortLink.getId());

        assertNotNull(savedShortLink);
        assertEquals(createdShortLink.getShortCode(), savedShortLink.getShortCode());
    }

    @Test
    @Transactional
    void shouldRetryWhenShortCodeAlreadyExists() {
        String conflictedCode = "abc12345";
        String availableCode = "def67890";

        ShortLink existingShortLink = new ShortLink();
        existingShortLink.setShortCode(conflictedCode);
        existingShortLink.setOriginalUrl("https://example.com/existing");

        shortLinkMapper.insert(existingShortLink);

        ShortLinkService fixedShortCodeService = new FixedShortCodeService(
                shortLinkMapper,
                stringRedisTemplate,
                conflictedCode,
                availableCode
        );

        ShortLink createdShortLink = fixedShortCodeService.createShortLink(
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
            super(stringRedisTemplate, shortLinkMapper);
            this.shortCodes.addAll(java.util.List.of(shortCodes));
        }

        @Override
        protected String generateShortCode() {
            return shortCodes.remove();
        }
    }
}
