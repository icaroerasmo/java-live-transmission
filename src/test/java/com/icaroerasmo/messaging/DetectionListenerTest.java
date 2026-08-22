package com.icaroerasmo.messaging;

import com.icaroerasmo.services.TranslationService;
import com.icaroerasmo.storage.DetectionStateStorage;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectionListenerTest {

    private static TranslationService newTranslationService() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return new TranslationService(messageSource);
    }

    @Test
    void shouldStoreTranslatedMovementLabelOnDetection() {
        DetectionStateStorage storage = new DetectionStateStorage();
        DetectionListener listener = new DetectionListener(storage, newTranslationService());

        listener.onDetection(new DetectionEvent("evt-1", "garagem1", "MOVEMENT_DETECTED", List.of()));

        assertTrue(storage.isActive("garagem1"));
        assertEquals("Movimento detectado", storage.primaryLabel());
        assertTrue(storage.primaryLabel().contains("Movimento"));
    }

    @Test
    void shouldIgnoreNullAndBlankCameraEvents() {
        DetectionStateStorage storage = new DetectionStateStorage();
        DetectionListener listener = new DetectionListener(storage, newTranslationService());

        listener.onDetection(new DetectionEvent("evt-1", null, "MOVEMENT_DETECTED", List.of()));
        listener.onDetection(new DetectionEvent("evt-2", "   ", "MOVEMENT_DETECTED", List.of()));
        listener.onDetection(null);

        assertTrue(storage.activeCameras().isEmpty());
        assertFalse(storage.isActive("garagem1"));
    }
}
