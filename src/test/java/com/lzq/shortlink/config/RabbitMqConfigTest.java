package com.lzq.shortlink.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * RabbitMQ 拓扑配置测试。
 */
class RabbitMqConfigTest {

    @Test
    void shouldCreateVisitQueueWithDeadLetterConfiguration() {
        RabbitMqConfig rabbitMqConfig = new RabbitMqConfig();

        Queue visitQueue = rabbitMqConfig.visitQueue();

        assertEquals(RabbitMqConfig.VISIT_QUEUE, visitQueue.getName());
        assertEquals(
                RabbitMqConfig.DLX_EXCHANGE,
                visitQueue.getArguments().get("x-dead-letter-exchange")
        );
        assertEquals(
                RabbitMqConfig.VISIT_DEAD_LETTER_ROUTING_KEY,
                visitQueue.getArguments().get("x-dead-letter-routing-key")
        );
    }

    @Test
    void shouldCustomizeRabbitTemplateForReliablePublishing() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitTemplateCustomizer customizer = new RabbitMqConfig()
                .rabbitTemplateCustomizer();

        customizer.customize(rabbitTemplate);

        verify(rabbitTemplate).setMandatory(true);
        verify(rabbitTemplate).setConfirmCallback(any());
        verify(rabbitTemplate).setReturnsCallback(any());
    }
}
