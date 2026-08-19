package com.icaroerasmo.services;

import com.icaroerasmo.properties.CameraProperties;
import com.icaroerasmo.properties.LiveTransmissionProperties;
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
public class FrameFeederService {

    @Autowired
    private LiveTransmissionProperties properties;

    private final Map<String, AtomicBoolean> feeders = new ConcurrentHashMap<>();
    private final Map<String, Thread> feederThreads = new ConcurrentHashMap<>();

    public void startFeeder(CameraProperties camera) {
        stopFeeder(camera.name());

        Path pipePath = Path.of(camera.pipePath());
        try {
            Files.deleteIfExists(pipePath);
            ProcessBuilder pb = new ProcessBuilder("mkfifo", pipePath.toString());
            pb.redirectErrorStream(true);
            Process mkfifo = pb.start();
            mkfifo.getInputStream().transferTo(new java.io.OutputStream() {
                @Override public void write(int b) {}
            });
            boolean finished = mkfifo.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) mkfifo.destroyForcibly();
            if (mkfifo.exitValue() != 0) {
                log.error("[FrameFeeder] Failed to create pipe for {}", camera.name());
                return;
            }
        } catch (Exception e) {
            log.error("[FrameFeeder] Failed to create pipe for {}", camera.name(), e);
            return;
        }

        AtomicBoolean running = new AtomicBoolean(true);
        feeders.put(camera.name(), running);

        double fps = Double.parseDouble(properties.output().fps());
        long intervalNanos = (long) (TimeUnit.SECONDS.toNanos(1) / fps);

        Thread feederThread = Thread.ofVirtual().name("frame-feeder-" + camera.name()).start(() -> {
            log.info("[FrameFeeder] Started feeder for {}", camera.name());
            Path currentFile = Path.of(camera.currentPath());

            try (FileOutputStream fos = new FileOutputStream(pipePath.toString())) {
                long nextWrite = System.nanoTime();
                while (running.get()) {
                    try {
                        byte[] bytes = Files.readAllBytes(currentFile);
                        if (bytes.length > 0) {
                            fos.write(bytes);
                            fos.flush();
                        }
                    } catch (IOException e) {
                        if (running.get()) {
                            log.debug("[FrameFeeder] Read error for {}: {}", camera.name(), e.getMessage());
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
                    log.debug("[FrameFeeder] Pipe closed for {}: {}", camera.name(), e.getMessage());
                }
            }

            log.info("[FrameFeeder] Stopped feeder for {}", camera.name());
        });

        feederThreads.put(camera.name(), feederThread);
    }

    public void stopFeeder(String cameraName) {
        AtomicBoolean running = feeders.remove(cameraName);
        if (running != null) {
            running.set(false);
        }

        Thread thread = feederThreads.remove(cameraName);
        if (thread != null) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Path pipePath = Path.of(properties.cameras().stream()
                .filter(c -> c.name().equals(cameraName))
                .findFirst()
                .map(CameraProperties::pipePath)
                .orElse("/tmp/" + cameraName + "-frames.pipe"));
        try {
            Files.deleteIfExists(pipePath);
        } catch (IOException e) {
            // ignore
        }
    }

    public void stopAll() {
        for (String name : feeders.keySet()) {
            stopFeeder(name);
        }
    }
}
