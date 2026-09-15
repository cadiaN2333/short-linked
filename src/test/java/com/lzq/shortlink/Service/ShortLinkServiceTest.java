package com.lzq.shortlink.service;

import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class ShortLinkServiceTest {

    @Autowired
    private ShortLinkService shortLinkService;

    @Autowired
    private ShortLinkMapper shortLinkMapper;

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
}