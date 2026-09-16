package com.lzq.shortlink.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
/**
 * RabbitMQ 访问事件拓扑配置。
 */
@Configuration
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
}