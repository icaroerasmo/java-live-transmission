package com.icaroerasmo.services;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class TranslationService {

    private static final Locale LOCALE = new Locale("pt", "BR");

    private final MessageSource messageSource;

    public TranslationService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String translate(String key, Object... args) {
        return messageSource.getMessage(key, args, LOCALE);
    }
}
