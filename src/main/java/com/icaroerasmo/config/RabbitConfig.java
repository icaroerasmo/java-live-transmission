package com.icaroerasmo.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Log4j2
@EnableRabbit
@Configuration
public class RabbitConfig {

    public static final String TELEGRAM_EXCHANGE = "telegram.exchange";
    public static final String TELEGRAM_QUEUE = "telegram.notifications";
    public static final String TELEGRAM_ROUTING_KEY = "telegram.notifications";
    public static final String TELEGRAM_DLX = "telegram.dlx";
    public static final String TELEGRAM_DLQ_ROUTING_KEY = "telegram.notifications.dlq";
    public static final String DETECTION_EXCHANGE = "detection.exchange";
    public static final String DETECTION_QUEUE = "detection.events";
    public static final String DETECTION_ROUTING_KEY = "detection.events";

    @Bean
    public DirectExchange telegramExchange() {
        return new DirectExchange(TELEGRAM_EXCHANGE, true, false);
    }

    @Bean
    public Queue telegramNotificationsQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", TELEGRAM_DLX);
        args.put("x-dead-letter-routing-key", TELEGRAM_DLQ_ROUTING_KEY);
        return QueueBuilder.durable(TELEGRAM_QUEUE).withArguments(args).build();
    }

    @Bean
    public Binding telegramNotificationsBinding() {
        return BindingBuilder.bind(telegramNotificationsQueue()).to(telegramExchange()).with(TELEGRAM_ROUTING_KEY);
    }

    @Bean
    public DirectExchange detectionExchange() {
        return new DirectExchange(DETECTION_EXCHANGE, true, false);
    }

    @Bean
    public Queue detectionQueue() {
        return QueueBuilder.durable(DETECTION_QUEUE).build();
    }

    @Bean
    public Binding detectionBinding() {
        return BindingBuilder.bind(detectionQueue()).to(detectionExchange()).with(DETECTION_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter("com.icaroerasmo", "java.util", "java.lang");
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("RabbitMQ message delivery not confirmed: {}", cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "RabbitMQ message returned: exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText()));
        return template;
    }

    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        return factory;
    }
}
