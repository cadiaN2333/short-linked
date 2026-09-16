package com.lzq.shortlink.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}