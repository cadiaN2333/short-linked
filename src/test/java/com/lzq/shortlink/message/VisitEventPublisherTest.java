package com.lzq.shortlink.message;

import com.lzq.shortlink.config.RabbitMqConfig;
import com.lzq.shortlink.entity.ShortLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * 访问事件发布器测试。
 */
@ExtendWith(MockitoExtension.class)
class VisitEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private VisitEventPublisher visitEventPublisher;

    private ShortLink shortLink;

    @BeforeEach
    void setUp() {
        shortLink = new ShortLink();
        shortLink.setId(1L);
        shortLink.setShortCode("abc12345");
    }

    @Test
    void shouldPublishEventWithShortLinkIdentity() {
        visitEventPublisher.publish(shortLink);

        ArgumentCaptor<VisitEvent> eventCaptor =
                ArgumentCaptor.forClass(VisitEvent.class);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.EVENT_EXCHANGE),
                eq(RabbitMqConfig.VISIT_ROUTING_KEY),
                eventCaptor.capture()
        );

        VisitEvent visitEvent = eventCaptor.getValue();

        assertNotNull(visitEvent.eventId());
        assertEquals(32, visitEvent.eventId().length());
        assertEquals(1L, visitEvent.shortLinkId());
        assertEquals("abc12345", visitEvent.shortCode());
        assertNotNull(visitEvent.visitedAt());
    }

    @Test
    void shouldIgnorePublishFailure() {
        doThrow(new AmqpException("模拟 RabbitMQ 连接失败"))
                .when(rabbitTemplate)
                .convertAndSend(
                        anyString(),
                        anyString(),
                        any(VisitEvent.class)
                );

        assertDoesNotThrow(() -> visitEventPublisher.publish(shortLink));
    }
}
