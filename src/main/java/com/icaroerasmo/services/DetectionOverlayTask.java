package com.icaroerasmo.services;

import com.icaroerasmo.properties.CameraProperties;
import com.icaroerasmo.properties.LiveTransmissionProperties;
import com.icaroerasmo.storage.DetectionStateStorage;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Log4j2
@Component
public class DetectionOverlayTask {

    private static final long TTL_MS = 30_000;

    private final DetectionStateStorage storage;
    private final FrameWorkerService frameWorkerService;
    private final LiveTransmissionProperties properties;

    public DetectionOverlayTask(DetectionStateStorage storage,
                                FrameWorkerService frameWorkerService,
                                LiveTransmissionProperties properties) {
        this.storage = storage;
        this.frameWorkerService = frameWorkerService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "1000")
    public void sweep() {
        Set<String> changedCameras = storage.detectChanges(TTL_MS);

        if (!changedCameras.isEmpty()) {
            storage.writeLabelFile();
            log.info("Detection overlay changed, restarting frame workers for cameras: {}", changedCameras);
            for (CameraProperties camera : properties.cameras()) {
                if (changedCameras.contains(camera.name())) {
                    frameWorkerService.restartWorkerForOverlay(camera);
                }
            }
        }
    }
}
