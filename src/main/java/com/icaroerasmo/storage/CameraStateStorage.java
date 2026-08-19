package com.icaroerasmo.storage;

import com.icaroerasmo.properties.CameraProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CameraStateStorage {

    private final Map<String, CameraState> states = new ConcurrentHashMap<>();

    public CameraState getOrCreate(CameraProperties camera) {
        return states.computeIfAbsent(camera.name(), name -> new CameraState(camera));
    }

    public CameraState get(String name) {
        return states.get(name);
    }

    public Map<String, CameraState> getAll() {
        return Map.copyOf(states);
    }

    public static class CameraState {
        private final CameraProperties camera;
        private volatile Process workerProcess;
        private volatile long workerStartedAt;
        private volatile boolean seenFrame;
        private volatile long lastFrameAt;
        private volatile String lastChecksum;
        private volatile Long sameFrameSince;
        private volatile long nextProbeAt;
        private volatile boolean available;

        public CameraState(CameraProperties camera) {
            this.camera = camera;
            this.available = false;
        }

        public CameraProperties camera() { return camera; }
        public Process workerProcess() { return workerProcess; }
        public void setWorkerProcess(Process process) { this.workerProcess = process; }
        public long workerStartedAt() { return workerStartedAt; }
        public void setWorkerStartedAt(long time) { this.workerStartedAt = time; }
        public boolean seenFrame() { return seenFrame; }
        public void setSeenFrame(boolean seen) { this.seenFrame = seen; }
        public long lastFrameAt() { return lastFrameAt; }
        public void setLastFrameAt(long time) { this.lastFrameAt = time; }
        public String lastChecksum() { return lastChecksum; }
        public void setLastChecksum(String checksum) { this.lastChecksum = checksum; }
        public Long sameFrameSince() { return sameFrameSince; }
        public void setSameFrameSince(Long time) { this.sameFrameSince = time; }
        public long nextProbeAt() { return nextProbeAt; }
        public void setNextProbeAt(long time) { this.nextProbeAt = time; }
        public boolean available() { return available; }
        public void setAvailable(boolean available) { this.available = available; }
    }
}
