package com.icaroerasmo.util;

import com.icaroerasmo.enums.MessagesEnum;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Log4j2
@Service
public class TelegramUtil {

    private static final String APP_TAG = "[Live Transmission] ";
    private static final long MIN_INTERVAL_MS = 2000;

    @Autowired
    private TelegramBot telegramBot;

    @Autowired
    private TranslationService translationService;

    @Autowired
    private com.icaroerasmo.properties.LiveTransmissionProperties properties;

    private final AtomicLong lastSentAt = new AtomicLong(0);

    public void sendMessage(MessagesEnum messageEnum, Object... args) {
        String translated = translationService.translate(messageEnum, args);
        String formatted = TelegramMessageFormatter.format(messageEnum, translated);
        String text = APP_TAG + formatted;

        long now = System.currentTimeMillis();
        long last = lastSentAt.get();
        long wait = MIN_INTERVAL_MS - (now - last);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        try {
            SendMessage request = new SendMessage(properties.telegram().chatId(), text);
            SendResponse response = telegramBot.execute(request);
            if (!response.isOk()) {
                log.error("Failed to send Telegram message: {}", response.description());
            } else {
                lastSentAt.set(System.currentTimeMillis());
            }
        } catch (Exception e) {
            log.error("Error sending Telegram message", e);
        }
    }
}
