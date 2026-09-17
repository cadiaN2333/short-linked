package com.lzq.shortlink.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import lombok.extern.slf4j.Slf4j;
/**
 * RabbitMQ 访问事件拓扑配置。
 */
@Configuration
@Slf4j
public class RabbitMqConfig {

    /** 访问事件交换机。 */
    public static final String EVENT_EXCHANGE = "short-link.events";

    /** 访问事件路由键。 */
    public static final String VISIT_ROUTING_KEY = "link.visit";

    /** 访问事件主队列。 */
    public static final String VISIT_QUEUE = "short-link.visit.queue";

    /** 死信交换机。 */
    public static final String DLX_EXCHANGE = "short-link.dlx";

    /** 访问事件死信路由键。 */
    public static final String VISIT_DEAD_LETTER_ROUTING_KEY = "link.visit.dead";

    /** 访问事件死信队列。 */
    public static final String VISIT_DEAD_LETTER_QUEUE = "short-link.visit.dlq";

    /**
     * 声明访问事件交换机。
     */
    @Bean
    public DirectExchange eventExchange() {
        return new DirectExchange(EVENT_EXCHANGE, true, false);
    }

    /**
     * 声明死信交换机。
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    /**
     * 声明访问事件队列，并配置失败消息的死信去向。
     */
    @Bean
    public Queue visitQueue() {
        return QueueBuilder.durable(VISIT_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(VISIT_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    /**
     * 声明访问事件死信队列。
     */
    @Bean
    public Queue visitDeadLetterQueue() {
        return QueueBuilder.durable(VISIT_DEAD_LETTER_QUEUE)
                .build();
    }

    /**
     * 绑定访问事件交换机和主队列。
     */
    @Bean
    public Binding visitBinding() {
        return BindingBuilder.bind(visitQueue())
                .to(eventExchange())
                .with(VISIT_ROUTING_KEY);
    }

    /**
     * 绑定死信交换机和死信队列。
     */
    @Bean
    public Binding visitDeadLetterBinding() {
        return BindingBuilder.bind(visitDeadLetterQueue())
                .to(deadLetterExchange())
                .with(VISIT_DEAD_LETTER_ROUTING_KEY);
    }

    /**
     * 将访问事件序列化为 JSON 消息。
     */
    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * 配置发布确认和不可路由消息回调。
     *
     * <p>回调只负责记录失败上下文，不把异常传播到短链接跳转主流程；
     * 实习项目阶段先保留 RabbitMQ 控制台人工排查入口。</p>
     */
    @Bean
    public RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return rabbitTemplate -> {
            rabbitTemplate.setMandatory(true);
            rabbitTemplate.setConfirmCallback(
                    (correlationData, acknowledged, cause) -> {
                        if (!acknowledged) {
                            String eventId = correlationData == null
                                    ? "unknown"
                                    : correlationData.getId();
                            log.warn(
                                    "RabbitMQ 发布确认失败，eventId={}, cause={}",
                                    eventId,
                                    cause
                            );
                        }
                    }
            );
            rabbitTemplate.setReturnsCallback(this::logReturnedMessage);
        };
    }

    /** 记录交换机无法路由的访问事件上下文。 */
    private void logReturnedMessage(ReturnedMessage returnedMessage) {
        log.warn(
                "RabbitMQ 消息不可路由，exchange={}, routingKey={}, replyCode={}, replyText={}",
                returnedMessage.getExchange(),
                returnedMessage.getRoutingKey(),
                returnedMessage.getReplyCode(),
                returnedMessage.getReplyText()
        );
    }
}
