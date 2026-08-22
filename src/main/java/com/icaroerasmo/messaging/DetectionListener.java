package com.icaroerasmo.messaging;

import com.icaroerasmo.services.TranslationService;
import com.icaroerasmo.storage.DetectionStateStorage;
import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class DetectionListener {

    private final DetectionStateStorage storage;
    private final TranslationService translationService;

    public DetectionListener(DetectionStateStorage storage, TranslationService translationService) {
        this.storage = storage;
        this.translationService = translationService;
    }

    @RabbitListener(queues = "detection.events")
    public void onDetection(DetectionEvent event) {
        if (event == null || event.cameraName() == null || event.cameraName().isBlank()) {
            log.warn("Ignoring invalid detection event");
            return;
        }

        String label;
        try {
            Object[] args = event.args() != null ? event.args().toArray() : new Object[0];
            label = translationService.translate(event.template(), args);
        } catch (Exception e) {
            log.warn("Failed to translate detection template '{}': {}", event.template(), e.getMessage());
            label = "Pessoa detectada";
        }

        log.info("Detection event received: camera={}, template={}, label={}", event.cameraName(), event.template(), label);
        storage.update(event.cameraName(), label);
        storage.writeLabelFile();
    }
}
