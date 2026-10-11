package com.lzq.shortlink.cache;

import com.lzq.shortlink.config.ShortLinkBloomProperties;
import com.lzq.shortlink.entity.ShortLink;
import com.lzq.shortlink.mapper.ShortLinkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 历史短码加载必须完整，比较规则不支持时必须绕过。 */
class ShortCodeBloomInitializerTest {
    private ShortLinkMapper mapper;
    private ShortCodeBloomFilter bloom;
    private ShortCodeBloomInitializer initializer;

    @BeforeEach
    void setUp() {
        mapper = mock(ShortLinkMapper.class);
        var properties = new ShortLinkBloomProperties(true, 10000, .001, 2);
        bloom = new ShortCodeBloomFilter(properties);
        initializer = new ShortCodeBloomInitializer(bloom, properties, mapper);
    }

    private ShortLink row(long id, String code, String status) {
        ShortLink row = new ShortLink();
        row.setId(id);
        row.setShortCode(code);
        row.setStatus(status);
        return row;
    }

    @Test
    void loadsAllPagesIncludingDisabledAndDeletedRows() {
        when(mapper.selectShortCodeCollation()).thenReturn("utf8mb4_bin");
        when(mapper.selectShortCodesAfterId(0, 2)).thenReturn(List.of(row(1, "active123", "ACTIVE"), row(5, "disabled123", "DISABLED")));
        when(mapper.selectShortCodesAfterId(5, 2)).thenReturn(List.of(row(9, "deleted123", "DELETED")));
        when(mapper.selectShortCodesAfterId(9, 2)).thenReturn(List.of());
        initializer.run(null);
        assertTrue(bloom.isReady());
        for (String code : List.of("active123", "disabled123", "deleted123")) assertTrue(bloom.mightContain(code));
        verify(mapper).selectShortCodesAfterId(9, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"utf8mb4_0900_ai_ci", "utf8mb4_general_ci", "unknown"})
    @NullSource
    void unsupportedComparisonNeverEnablesBlocking(String collation) {
        when(mapper.selectShortCodeCollation()).thenReturn(collation);
        initializer.run(null);
        assertFalse(bloom.isReady());
        assertTrue(bloom.mightContain("missing123"));
        verify(mapper, never()).selectShortCodesAfterId(anyLong(), anyInt());
    }

    @Test
    void failedLaterPageDoesNotPublishPartialFilter() {
        when(mapper.selectShortCodeCollation()).thenReturn("utf8mb4_bin");
        when(mapper.selectShortCodesAfterId(0, 2)).thenReturn(List.of(row(1, "loaded123", "ACTIVE")));
        when(mapper.selectShortCodesAfterId(1, 2)).thenThrow(new IllegalStateException("模拟第二批失败"));
        initializer.run(null);
        assertFalse(bloom.isReady());
        assertTrue(bloom.mightContain("notLoaded123"));
    }

    @Test
    void invalidCursorDoesNotLoopForeverOrPublishFilter() {
        when(mapper.selectShortCodeCollation()).thenReturn("utf8mb4_bin");
        when(mapper.selectShortCodesAfterId(0, 2)).thenReturn(List.of(row(1, "first123", "ACTIVE"), row(1, "second123", "ACTIVE")));
        initializer.run(null);
        assertFalse(bloom.isReady());
        assertTrue(bloom.mightContain("missing123"));
    }

    @Test
    void disabledConfigurationDoesNotReadDatabase() {
        var properties = new ShortLinkBloomProperties(false, 10000, .001, 2);
        var disabled = new ShortCodeBloomFilter(properties);
        new ShortCodeBloomInitializer(disabled, properties, mapper).run(null);
        assertFalse(disabled.isReady());
        verifyNoInteractions(mapper);
    }
}
