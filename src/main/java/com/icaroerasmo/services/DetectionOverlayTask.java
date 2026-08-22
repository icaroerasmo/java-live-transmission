package com.icaroerasmo.services;

import com.icaroerasmo.storage.DetectionStateStorage;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Log4j2
@Component
public class DetectionOverlayTask {

    private static final long TTL_MS = 10_000;

    private final DetectionStateStorage storage;
    private final CompositorService compositorService;

    public DetectionOverlayTask(DetectionStateStorage storage, CompositorService compositorService) {
        this.storage = storage;
        this.compositorService = compositorService;
    }

    @Scheduled(fixedDelayString = "1000")
    public void sweep() {
        boolean expired = storage.expireStale(TTL_MS);
        Set<String> active = storage.activeCameras();
        boolean changed = storage.markRenderedIfChanged(active);

        if (expired || changed) {
            storage.writeLabelFile();
        }
        if (changed) {
            log.info("Detection overlay changed, restarting compositor. Active cameras: {}", active);
            compositorService.start();
        }
    }
}
