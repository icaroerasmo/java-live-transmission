package com.icaroerasmo.services;

import com.icaroerasmo.storage.DetectionStateStorage;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Log4j2
@Component
public class DetectionOverlayTask {

    private static final long TTL_MS = 5_000;

    private final DetectionStateStorage storage;

    public DetectionOverlayTask(DetectionStateStorage storage) {
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "1000")
    public void sweep() {
        Set<String> changedCameras = storage.detectChanges(TTL_MS);
        if (!changedCameras.isEmpty()) {
            storage.writeLabelFile();
        }
    }
}
