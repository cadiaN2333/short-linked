package com.lzq.shortlink.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import com.lzq.shortlink.entity.ShortLink;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 短链接 Mapper 测试。
 */
@SpringBootTest
class ShortLinkMapperTest {

    @Autowired
    private ShortLinkMapper shortLinkMapper;

    @Test
    void shouldConnectToShortLinkTable() {
        Long count = shortLinkMapper.selectCount(null);

        assertNotNull(count);
    }
    @Test
    @Transactional
    void shouldInsertAndFindShortLink() {
        String shortCode = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        ShortLink shortLink = new ShortLink();
        shortLink.setShortCode(shortCode);
        shortLink.setOriginalUrl("https://example.com/test");
        shortLink.setManageToken(UUID.randomUUID().toString().replace("-", ""));

        int affectedRows = shortLinkMapper.insert(shortLink);

        assertEquals(1, affectedRows);
        assertNotNull(shortLink.getId());

        ShortLink savedShortLink = shortLinkMapper.selectById(shortLink.getId());

        assertNotNull(savedShortLink);
        assertEquals(shortCode, savedShortLink.getShortCode());
        assertEquals("https://example.com/test", savedShortLink.getOriginalUrl());
        assertEquals(shortLink.getManageToken(), savedShortLink.getManageToken());
        assertNull(savedShortLink.getExpireAt());
    }
}
