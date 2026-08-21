package com.icaroerasmo.messaging;

import com.icaroerasmo.enums.MessagesEnum;
import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Log4j2
@Service
public class NotificationPublisher {

    private static final String SENDER = "live-transmission";
    private static final String EXCHANGE = "telegram.exchange";
    private static final String ROUTING_KEY = "telegram.notifications";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void publish(MessagesEnum template, Object... args) {
        List<String> stringArgs = Arrays.stream(args)
                .map(String::valueOf)
                .toList();

        NotificationMessage message = new NotificationMessage(
                UUID.randomUUID().toString(),
                SENDER,
                NotificationMessage.MediaType.TEXT,
                template.name(),
                stringArgs,
                null,
                null,
                null,
                false);

        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
        } catch (Exception e) {
            log.error("Error publishing notification to RabbitMQ: {}", e.getMessage());
            log.debug("Error publishing notification to RabbitMQ", e);
        }
    }
}
