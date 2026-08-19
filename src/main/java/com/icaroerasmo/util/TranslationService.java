package com.icaroerasmo.util;

import com.icaroerasmo.enums.MessagesEnum;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.TimeZone;

@Service
public class TranslationService {

    private static final Locale LOCALE = new Locale("pt", "BR");

    @Autowired
    private MessageSource messageSource;

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Bahia"));
        Locale.setDefault(LOCALE);
    }

    public String translate(MessagesEnum messageEnum, Object... args) {
        return messageSource.getMessage(messageEnum.getKey(), args, LOCALE);
    }
}
