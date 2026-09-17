package com.lzq.shortlink.message;

import com.lzq.shortlink.config.RabbitMqConfig;
import com.lzq.shortlink.entity.ShortLink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 短链接访问事件发布器。
 */
@Component
@Slf4j
public class VisitEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public VisitEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发布一次短链接访问事件。
     * RabbitMQ 不可用时不影响跳转主流程。
     *
     * @param shortLink 被访问的短链接
     */
    public void publish(ShortLink shortLink) {
        VisitEvent visitEvent = new VisitEvent(
                UUID.randomUUID().toString().replace("-", ""),
                shortLink.getId(),
                shortLink.getShortCode(),
                LocalDateTime.now()
        );

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EVENT_EXCHANGE,
                    RabbitMqConfig.VISIT_ROUTING_KEY,
                    visitEvent,
                    new CorrelationData(visitEvent.eventId())
            );
        } catch (AmqpException exception) {
            log.warn(
                    "访问事件发布失败，跳转不受影响，shortCode={}",
                    shortLink.getShortCode(),
                    exception
            );
        }
    }
}
