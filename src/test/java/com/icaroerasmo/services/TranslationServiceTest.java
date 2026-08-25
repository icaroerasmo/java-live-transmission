package com.icaroerasmo.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationServiceTest {

    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        translationService = new TranslationService(messageSource);
    }

    @Test
    void shouldTranslatePersonDetectedToPtBr() {
        assertEquals("Pessoa detectada", translationService.translate("PERSON_DETECTED"));
    }

    @Test
    void shouldTranslateMovementDetectedToPtBr() {
        assertEquals("Movimento detectado", translationService.translate("MOVEMENT_DETECTED"));
    }

    @Test
    void shouldTranslatePetDetectedToPtBr() {
        assertEquals("Animal detectado", translationService.translate("PET_DETECTED"));
    }

    @Test
    void shouldTranslateCarDetectedToPtBr() {
        assertEquals("Carro detectado", translationService.translate("CAR_DETECTED"));
    }
}
