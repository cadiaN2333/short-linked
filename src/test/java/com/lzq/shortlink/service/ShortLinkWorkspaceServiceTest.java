package com.lzq.shortlink.service;

import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import com.lzq.shortlink.message.VisitEventPublisher;
import com.lzq.shortlink.service.impl.ShortLinkServiceImpl;
import com.lzq.shortlink.service.ShortLinkPageResult;
import com.lzq.shortlink.exception.ReservedShortCodeException;
import com.lzq.shortlink.exception.ShortCodeAlreadyExistsException;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 短链接创建必须绑定工作空间测试。 */
@ExtendWith(MockitoExtension.class)
class ShortLinkWorkspaceServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ShortLinkMapper shortLinkMapper;

    @Mock
    private VisitEventPublisher visitEventPublisher;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ShortLinkServiceImpl shortLinkService;

    @BeforeEach
    void setUp() {
        shortLinkService = new ShortLinkServiceImpl(
                stringRedisTemplate,
                shortLinkMapper,
                visitEventPublisher
        );
    }

    @Test
    void shouldNotReturnDisabledShortLinkFromCache() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("short-link:missing:abc12345"))
                .thenReturn(null);
        when(valueOperations.get("short-link:abc12345"))
                .thenReturn("https://example.com");
        when(valueOperations.get("short-link:id:abc12345"))
                .thenReturn("20");
        when(valueOperations.get("short-link:status:abc12345"))
                .thenReturn("DISABLED");

        ShortLink result = shortLinkService.findAvailableShortLink("abc12345");

        org.junit.jupiter.api.Assertions.assertNull(result);
        verifyNoInteractions(shortLinkMapper);
    }

    @Test
    void shouldEvictCorruptedCachedIdAndFallbackToDatabase() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("short-link:missing:abc12345"))
                .thenReturn(null);
        ShortLink databaseShortLink = new ShortLink();
        databaseShortLink.setId(20L);
        databaseShortLink.setShortCode("abc12345");
        databaseShortLink.setOriginalUrl("https://example.com");
        databaseShortLink.setStatus("ACTIVE");

        when(valueOperations.get("short-link:abc12345"))
                .thenReturn("https://example.com");
        when(valueOperations.get("short-link:id:abc12345"))
                .thenReturn("not-a-number");
        when(valueOperations.get("short-link:status:abc12345"))
                .thenReturn("ACTIVE");
        when(shortLinkMapper.selectOne(any()))
                .thenReturn(databaseShortLink);

        ShortLink result = shortLinkService.findAvailableShortLink("abc12345");

        assertEquals(20L, result.getId());
        verify(stringRedisTemplate).delete("short-link:abc12345");
        verify(stringRedisTemplate).delete("short-link:id:abc12345");
        verify(stringRedisTemplate).delete("short-link:status:abc12345");
    }

    @Test
    void shouldWriteShortLivedNegativeCacheWhenShortLinkDoesNotExist() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(shortLinkMapper.selectOne(any())).thenReturn(null);

        ShortLink result = shortLinkService.findAvailableShortLink("missing01");

        org.junit.jupiter.api.Assertions.assertNull(result);
        verify(valueOperations).set(
                "short-link:missing:missing01",
                "1",
                java.time.Duration.ofSeconds(30)
        );
    }

    @Test
    void shouldPersistWorkspaceIdWhenCreatingShortLink() {
        doAnswer(invocation -> {
            ShortLink shortLink = invocation.getArgument(0);
            shortLink.setId(10L);
            return 1;
        }).when(shortLinkMapper).insert(any(ShortLink.class));

        ShortLink result = shortLinkService.createShortLink(
                100L,
                "https://example.com",
                null
        );

        assertEquals(100L, result.getWorkspaceId());
        verify(shortLinkMapper).insert(any(ShortLink.class));
    }

    @Test
    void shouldPersistRequestedCustomShortCode() {
        doAnswer(invocation -> {
            ShortLink shortLink = invocation.getArgument(0);
            shortLink.setId(11L);
            return 1;
        }).when(shortLinkMapper).insert(any(ShortLink.class));

        ShortLink result = shortLinkService.createShortLink(
                100L,
                "https://example.com/custom",
                null,
                "promo2026"
        );

        assertEquals("promo2026", result.getShortCode());
    }

    @Test
    void shouldRejectReservedCustomShortCode() {
        org.junit.jupiter.api.Assertions.assertThrows(
                ReservedShortCodeException.class,
                () -> shortLinkService.createShortLink(
                        100L,
                        "https://example.com/reserved",
                        null,
                        "api"
                )
        );
    }

    @Test
    void shouldReturnConflictWhenCustomShortCodeAlreadyExists() {
        when(shortLinkMapper.insert(any(ShortLink.class)))
                .thenThrow(new DuplicateKeyException("uk_short_code"));

        org.junit.jupiter.api.Assertions.assertThrows(
                ShortCodeAlreadyExistsException.class,
                () -> shortLinkService.createShortLink(
                        100L,
                        "https://example.com/duplicate",
                        null,
                        "promo2026"
                )
        );
    }

    @Test
    void shouldPageShortLinksByWorkspaceId() {
        ShortLink shortLink = new ShortLink();
        shortLink.setId(20L);
        shortLink.setWorkspaceId(100L);
        shortLink.setShortCode("abc12345");
        shortLink.setOriginalUrl("https://example.com");

        when(shortLinkMapper.selectPageByWorkspaceId(100L, 20L, 0L))
                .thenReturn(java.util.List.of(shortLink));
        when(shortLinkMapper.countByWorkspaceId(100L)).thenReturn(1L);

        ShortLinkPageResult result = shortLinkService.listShortLinks(
                100L,
                1,
                20
        );

        assertEquals(1L, result.total());
        assertEquals(1, result.items().size());
        assertEquals(100L, result.items().get(0).getWorkspaceId());
        verify(shortLinkMapper).selectPageByWorkspaceId(100L, 20L, 0L);
    }

    @Test
    void shouldFilterShortLinksByStatusAndKeyword() {
        ShortLink shortLink = new ShortLink();
        shortLink.setId(21L);
        shortLink.setWorkspaceId(100L);
        shortLink.setShortCode("disabled1");
        shortLink.setOriginalUrl("https://example.com/disabled");
        shortLink.setStatus("DISABLED");

        when(shortLinkMapper.selectPageByWorkspaceIdAndFilter(
                100L,
                "DISABLED",
                "disabled",
                20L,
                0L
        )).thenReturn(java.util.List.of(shortLink));
        when(shortLinkMapper.countByWorkspaceIdAndFilter(
                100L,
                "DISABLED",
                "disabled"
        )).thenReturn(1L);

        ShortLinkPageResult result = shortLinkService.listShortLinks(
                100L,
                1,
                20,
                "DISABLED",
                "disabled"
        );

        assertEquals(1L, result.total());
        assertEquals("DISABLED", result.items().get(0).getStatus());
        verify(shortLinkMapper).countByWorkspaceIdAndFilter(
                100L,
                "DISABLED",
                "disabled"
        );
    }

    @Test
    void shouldFindShortLinkOnlyInsideWorkspace() {
        ShortLink shortLink = new ShortLink();
        shortLink.setId(20L);
        shortLink.setWorkspaceId(100L);

        when(shortLinkMapper.selectByWorkspaceIdAndId(100L, 20L))
                .thenReturn(shortLink);

        ShortLink result = shortLinkService.findShortLinkById(100L, 20L);

        assertEquals(20L, result.getId());
        assertEquals(100L, result.getWorkspaceId());
    }

    @Test
    void shouldUpdateShortLinkInsideWorkspace() {
        ShortLink shortLink = new ShortLink();
        shortLink.setId(20L);
        shortLink.setWorkspaceId(100L);
        shortLink.setOriginalUrl("https://example.com/old");

        when(shortLinkMapper.selectByWorkspaceIdAndId(100L, 20L))
                .thenReturn(shortLink);
        org.mockito.stubbing.Answer<Integer> updateAnswer = invocation -> {
            shortLink.setOriginalUrl("https://example.com/new");
            return 1;
        };
        when(shortLinkMapper.updateContentByWorkspaceIdAndId(
                100L,
                20L,
                "https://example.com/new",
                null
        )).thenAnswer(updateAnswer);

        ShortLink result = shortLinkService.updateShortLink(
                100L,
                20L,
                "https://example.com/new",
                null
        );

        assertEquals("https://example.com/new", result.getOriginalUrl());
        verify(shortLinkMapper).updateContentByWorkspaceIdAndId(
                100L,
                20L,
                "https://example.com/new",
                null
        );
    }

    @Test
    void shouldChangeStatusInsideWorkspace() {
        ShortLink shortLink = new ShortLink();
        shortLink.setId(20L);
        shortLink.setWorkspaceId(100L);
        shortLink.setStatus("ACTIVE");

        when(shortLinkMapper.selectByWorkspaceIdAndId(100L, 20L))
                .thenReturn(shortLink);
        when(shortLinkMapper.updateStatusByWorkspaceIdAndId(
                100L,
                20L,
                "DISABLED"
        )).thenAnswer(invocation -> {
            shortLink.setStatus("DISABLED");
            return 1;
        });

        ShortLink result = shortLinkService.changeShortLinkStatus(
                100L,
                20L,
                "DISABLED"
        );

        assertEquals("DISABLED", result.getStatus());
    }

    @Test
    void shouldSoftDeleteInsideWorkspace() {
        ShortLink shortLink = new ShortLink();
        shortLink.setId(20L);
        shortLink.setWorkspaceId(100L);
        shortLink.setShortCode("abc12345");
        when(shortLinkMapper.selectByWorkspaceIdAndId(100L, 20L))
                .thenReturn(shortLink);
        when(shortLinkMapper.updateStatusByWorkspaceIdAndId(
                100L,
                20L,
                "DELETED"
        )).thenReturn(1);

        boolean deleted = shortLinkService.deleteShortLink(100L, 20L);

        org.junit.jupiter.api.Assertions.assertTrue(deleted);
    }
}
