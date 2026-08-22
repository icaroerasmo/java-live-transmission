package com.icaroerasmo.services;

import com.icaroerasmo.properties.CameraProperties;
import com.icaroerasmo.properties.LiveTransmissionProperties;
import com.icaroerasmo.storage.DetectionStateStorage;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

@Log4j2
@Service
public class BorderFeederService {

    private static final int BORDER_THICKNESS = 4;

    @Autowired
    private LiveTransmissionProperties properties;

    @Autowired
    private DetectionStateStorage detectionStateStorage;

    private final Map<String, AtomicBoolean> feeders = new ConcurrentHashMap<>();
    private final Map<String, Thread> feederThreads = new ConcurrentHashMap<>();

    private volatile byte[] redBorderBytes = new byte[0];
    private volatile byte[] clearBorderBytes = new byte[0];

    public void generateBorderImages() {
        try {
            int width = Integer.parseInt(properties.panel().width());
            int height = Integer.parseInt(properties.panel().height());
            int t = BORDER_THICKNESS;
            int pixelCount = width * height;

            byte[] red = new byte[pixelCount * 4];
            byte[] clear = new byte[pixelCount * 4]; // all zeros = fully transparent

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (x < t || x >= width - t || y < t || y >= height - t) {
                        int idx = (y * width + x) * 4;
                        red[idx] = (byte) 255;     // R
                        red[idx + 1] = 0;          // G
                        red[idx + 2] = 0;          // B
                        red[idx + 3] = (byte) 255; // A
                    }
                }
            }

            this.redBorderBytes = red;
            this.clearBorderBytes = clear;

            log.info("[BorderFeeder] Generated border buffers ({}x{}, thickness {})", width, height, t);
        } catch (Exception e) {
            log.error("[BorderFeeder] Failed to generate border buffers", e);
        }
    }

    public void start(CameraProperties camera) {
        stop(camera.name());

        Path pipePath = Path.of(camera.borderPipePath());
        try {
            Files.deleteIfExists(pipePath);
            ProcessBuilder pb = new ProcessBuilder("mkfifo", pipePath.toString());
            pb.redirectErrorStream(true);
            Process mkfifo = pb.start();
            mkfifo.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            boolean finished = mkfifo.waitFor(5, TimeUnit.SECONDS);
            if (!finished) mkfifo.destroyForcibly();
            if (mkfifo.exitValue() != 0) {
                log.error("[BorderFeeder] Failed to create pipe for {}", camera.name());
                return;
            }
        } catch (Exception e) {
            log.error("[BorderFeeder] Failed to create pipe for {}", camera.name(), e);
            return;
        }

        AtomicBoolean running = new AtomicBoolean(true);
        feeders.put(camera.name(), running);

        double fps = Double.parseDouble(properties.output().fps());
        long intervalNanos = (long) (TimeUnit.SECONDS.toNanos(1) / fps);

        Thread feederThread = Thread.ofVirtual().name("border-feeder-" + camera.name()).start(() -> {
            log.info("[BorderFeeder] Started feeder for {}", camera.name());
            try (FileOutputStream fos = new FileOutputStream(pipePath.toString())) {
                long nextWrite = System.nanoTime();
                while (running.get()) {
                    try {
                        byte[] bytes = detectionStateStorage.isActive(camera.name()) ? redBorderBytes : clearBorderBytes;
                        if (bytes != null && bytes.length > 0) {
                            fos.write(bytes);
                            fos.flush();
                        }
                    } catch (IOException e) {
                        if (running.get()) {
                            log.debug("[BorderFeeder] Write error for {}: {}", camera.name(), e.getMessage());
                        }
                    }
                    nextWrite += intervalNanos;
                    long delay = nextWrite - System.nanoTime();
                    if (delay > 0) {
                        LockSupport.parkNanos(delay);
                    } else if (delay < -intervalNanos) {
                        nextWrite = System.nanoTime();
                    }
                }
            } catch (IOException e) {
                if (running.get()) {
                    log.debug("[BorderFeeder] Pipe closed for {}: {}", camera.name(), e.getMessage());
                }
            }
            log.info("[BorderFeeder] Stopped feeder for {}", camera.name());
        });

        feederThreads.put(camera.name(), feederThread);
    }

    public void stop(String cameraName) {
        AtomicBoolean running = feeders.remove(cameraName);
        if (running != null) running.set(false);
        Thread thread = feederThreads.remove(cameraName);
        if (thread != null) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            Files.deleteIfExists(Path.of("/tmp/" + cameraName.replaceAll("\\s+", "") + "-border.pipe"));
        } catch (IOException ignored) {
        }
    }

    public void stopAll() {
        for (String name : feeders.keySet()) {
            stop(name);
        }
    }
}
