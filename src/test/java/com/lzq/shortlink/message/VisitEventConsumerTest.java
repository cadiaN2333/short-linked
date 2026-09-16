package com.lzq.shortlink.message;

import com.lzq.shortlink.mapper.ShortLinkDailyStatMapper;
import com.lzq.shortlink.mapper.ShortLinkVisitEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 访问事件消费者测试。
 */
@ExtendWith(MockitoExtension.class)
class VisitEventConsumerTest {

    @Mock
    private ShortLinkVisitEventMapper shortLinkVisitEventMapper;

    @Mock
    private ShortLinkDailyStatMapper shortLinkDailyStatMapper;

    @InjectMocks
    private VisitEventConsumer visitEventConsumer;

    private VisitEvent visitEvent;

    @BeforeEach
    void setUp() {
        visitEvent = new VisitEvent(
                "a".repeat(32),
                1L,
                "abc12345",
                LocalDateTime.of(2026, 9, 16, 20, 0)
        );
    }

    @Test
    void shouldIncrementDailyPvOnlyForNewEvent() {
        when(shortLinkVisitEventMapper.insertIgnore(any())).thenReturn(1);

        visitEventConsumer.consume(visitEvent);

        verify(shortLinkDailyStatMapper).incrementPv(
                1L,
                LocalDate.of(2026, 9, 16)
        );
    }

    @Test
    void shouldSkipDailyPvWhenEventAlreadyExists() {
        when(shortLinkVisitEventMapper.insertIgnore(any())).thenReturn(0);

        visitEventConsumer.consume(visitEvent);

        verifyNoInteractions(shortLinkDailyStatMapper);
    }
}