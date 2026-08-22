package com.icaroerasmo.storage;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@Component
public class DetectionStateStorage {

    public static final String LABEL_FILE = "/tmp/detection-label.txt";
    public static final String FONT_FILE = "/usr/share/fonts/Adwaita/AdwaitaSans-Regular.ttf";

    private final Map<String, DetectionState> states = new ConcurrentHashMap<>();
    private final Set<String> renderedActiveCameras = ConcurrentHashMap.newKeySet();

    public void update(String cameraName, String label) {
        states.put(cameraName, new DetectionState(label, System.currentTimeMillis()));
    }

    public Set<String> detectChanges(long ttlMs) {
        long now = System.currentTimeMillis();
        states.entrySet().removeIf(e -> now - e.getValue().detectedAt() > ttlMs);

        Set<String> active = new HashSet<>(states.keySet());
        Set<String> changed = new HashSet<>();
        for (String camera : renderedActiveCameras) {
            if (!active.contains(camera)) {
                changed.add(camera);
            }
        }
        for (String camera : active) {
            if (!renderedActiveCameras.contains(camera)) {
                changed.add(camera);
            }
        }

        renderedActiveCameras.clear();
        renderedActiveCameras.addAll(active);
        return changed;
    }

    public Set<String> activeCameras() {
        return new HashSet<>(states.keySet());
    }

    public String primaryLabel() {
        DetectionState latest = null;
        for (DetectionState state : states.values()) {
            if (latest == null || state.detectedAt() > latest.detectedAt()) {
                latest = state;
            }
        }
        return latest == null ? "" : latest.label();
    }

    public void ensureLabelFile() {
        writeLabelFile();
    }

    public void writeLabelFile() {
        try {
            Files.writeString(Path.of(LABEL_FILE), primaryLabel());
        } catch (Exception e) {
            log.warn("Failed to write detection label file: {}", e.getMessage());
        }
    }

    public record DetectionState(String label, long detectedAt) {
    }
}
