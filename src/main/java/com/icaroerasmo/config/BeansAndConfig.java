package com.icaroerasmo.config;

import com.icaroerasmo.properties.LiveTransmissionProperties;
import com.icaroerasmo.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(LiveTransmissionProperties.class)
public class BeansAndConfig {

    @Bean
    public TelegramBot telegramBot(LiveTransmissionProperties properties) {
        TelegramProperties tg = properties.telegram();
        return new TelegramBot(tg.botToken());
    }

    @Bean
    public ExecutorService executorService() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }
}
