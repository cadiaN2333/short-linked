package com.lzq.shortlink.service;

import com.lzq.shortlink.dto.DailyPvResponse;
import com.lzq.shortlink.exception.InvalidStatisticsRangeException;
import com.lzq.shortlink.mapper.ShortLinkDailyStatMapper;
import com.lzq.shortlink.mapper.ShortLinkDailyStatRow;
import com.lzq.shortlink.service.impl.ShortLinkAnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** 短链接访问分析服务测试。 */
@ExtendWith(MockitoExtension.class)
class ShortLinkAnalyticsServiceTest {

    @Mock
    private ShortLinkDailyStatMapper mapper;

    private ShortLinkAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new ShortLinkAnalyticsServiceImpl(mapper);
    }

    @Test
    void shouldFillMissingDatesWithZeroPv() {
        Long shortLinkId = 59L;
        LocalDate from = LocalDate.of(2026, 9, 14);
        LocalDate to = LocalDate.of(2026, 9, 16);

        when(mapper.selectByShortLinkIdAndDateBetween(shortLinkId, from, to))
                .thenReturn(List.of(
                        new ShortLinkDailyStatRow(
                                LocalDate.of(2026, 9, 16), 2L
                        )
                ));

        List<DailyPvResponse> result = service.queryDailyPv(
                shortLinkId, from, to
        );

        assertEquals(
                List.of(
                        new DailyPvResponse(
                                LocalDate.of(2026, 9, 14), 0L
                        ),
                        new DailyPvResponse(
                                LocalDate.of(2026, 9, 15), 0L
                        ),
                        new DailyPvResponse(
                                LocalDate.of(2026, 9, 16), 2L
                        )
                ),
                result
        );
    }

    @Test
    void shouldRejectReversedDateRange() {
        assertThrows(
                InvalidStatisticsRangeException.class,
                () -> service.queryDailyPv(
                        59L,
                        LocalDate.of(2026, 9, 16),
                        LocalDate.of(2026, 9, 15)
                )
        );
    }

    @Test
    void shouldRejectRangeLongerThanNinetyDays() {
        assertThrows(
                InvalidStatisticsRangeException.class,
                () -> service.queryDailyPv(
                        59L,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 8, 30)
                )
        );
    }
}
