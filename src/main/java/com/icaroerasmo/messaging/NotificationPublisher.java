package com.icaroerasmo.messaging;

import com.icaroerasmo.enums.MessagesEnum;
import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Log4j2
@Service
public class NotificationPublisher {

    private static final String SENDER = "live-transmission";
    private static final String EXCHANGE = "telegram.exchange";
    private static final String ROUTING_KEY = "telegram.notifications";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Async
    public void publish(MessagesEnum template, Object... args) {
        List<String> stringArgs = Arrays.stream(args)
                .map(String::valueOf)
                .toList();

        String sentAt = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        NotificationMessage message = new NotificationMessage(
                UUID.randomUUID().toString(),
                SENDER,
                NotificationMessage.MediaType.TEXT,
                template.name(),
                stringArgs,
                null,
                null,
                null,
                false,
                sentAt);

        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
        } catch (Exception e) {
            log.error("Error publishing notification to RabbitMQ: {}", e.getMessage());
            log.debug("Error publishing notification to RabbitMQ", e);
        }
    }
}
