package com.icaroerasmo.services;

import com.icaroerasmo.parsers.AudioWorkerCommandParser;
import com.icaroerasmo.properties.CameraProperties;
import com.icaroerasmo.properties.LiveTransmissionProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

@Log4j2
@Service
public class AudioStreamService {

    private static final int CHUNK_MILLISECONDS = 100;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int QUEUE_CAPACITY = 10;
    private static final int TARGET_QUEUE_CHUNKS = 2;

    @Autowired
    private LiveTransmissionProperties properties;

    private final Map<String, Process> workers = new ConcurrentHashMap<>();
    private final Map<String, LinkedBlockingDeque<byte[]>> queues = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> feeders = new ConcurrentHashMap<>();
    private final Map<String, Thread> feederThreads = new ConcurrentHashMap<>();
    private final Map<String, Object> cameraLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> nextWorkerStartAt = new ConcurrentHashMap<>();

    public void start(CameraProperties camera) {
        stopFeeder(camera.name());
        if (!createPipe(camera)) {
            return;
        }
        queues.computeIfAbsent(camera.name(), ignored -> new LinkedBlockingDeque<>(QUEUE_CAPACITY));
        startFeeder(camera);
        startWorker(camera);
    }

    public void startWorker(CameraProperties camera) {
        synchronized (cameraLock(camera.name())) {
            stopWorkerLocked(camera.name());
            nextWorkerStartAt.remove(camera.name());

            LinkedBlockingDeque<byte[]> queue =
                    queues.computeIfAbsent(camera.name(), ignored -> new LinkedBlockingDeque<>(QUEUE_CAPACITY));
            queue.clear();

            try {
                Process process = new ProcessBuilder(AudioWorkerCommandParser.build(camera, properties)).start();
                workers.put(camera.name(), process);
                startAudioReader(camera, process, queue);
                startErrorReader(camera, process);
                startExitMonitor(camera, process);
                log.info("[AudioWorker] Started audio worker for {} with pid {}", camera.name(), process.pid());
            } catch (IOException e) {
                scheduleWorkerRestart(camera.name());
                log.error("[AudioWorker] Failed to start audio worker for {}", camera.name(), e);
            }
        }
    }

    public void stopWorker(String cameraName) {
        synchronized (cameraLock(cameraName)) {
            stopWorkerLocked(cameraName);
            nextWorkerStartAt.remove(cameraName);
        }
    }

    public void restartWorkerIfDue(CameraProperties camera) {
        synchronized (cameraLock(camera.name())) {
            Process process = workers.get(camera.name());
            if (process != null && !process.isAlive()) {
                workers.remove(camera.name(), process);
                nextWorkerStartAt.putIfAbsent(
                        camera.name(),
                        System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(
                                properties.watchdog().cameraRetrySeconds()));
            }

            long nextStart = nextWorkerStartAt.getOrDefault(camera.name(), 0L);
            if (!isWorkerAlive(camera.name()) && System.currentTimeMillis() >= nextStart) {
                startWorker(camera);
            }
        }
    }

    private void stopWorkerLocked(String cameraName) {
        Process process = workers.remove(cameraName);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        LinkedBlockingDeque<byte[]> queue = queues.get(cameraName);
        if (queue != null) {
            queue.clear();
        }
    }

    public boolean isWorkerAlive(String cameraName) {
        Process process = workers.get(cameraName);
        return process != null && process.isAlive();
    }

    public void stopAll() {
        for (String name : workers.keySet()) {
            stopWorker(name);
        }
        for (String name : feeders.keySet()) {
            stopFeeder(name);
        }
        queues.clear();
        cameraLocks.clear();
        nextWorkerStartAt.clear();
    }

    private boolean createPipe(CameraProperties camera) {
        Path pipePath = Path.of(camera.audioPipePath());
        try {
            Files.deleteIfExists(pipePath);
            Process process = new ProcessBuilder("mkfifo", pipePath.toString()).start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Timed out creating " + pipePath);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("mkfifo exited with code " + process.exitValue());
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            log.error("[AudioFeeder] Failed to create audio pipe for {}", camera.name(), e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[AudioFeeder] Interrupted while creating audio pipe for {}", camera.name(), e);
            return false;
        }
    }

    private void startFeeder(CameraProperties camera) {
        AtomicBoolean running = new AtomicBoolean(true);
        feeders.put(camera.name(), running);
        int sampleRate = Integer.parseInt(properties.output().audioSampleRate());
        int chunkSize = sampleRate * BYTES_PER_SAMPLE * CHUNK_MILLISECONDS / 1000;
        byte[] silence = new byte[chunkSize];
        long intervalNanos = TimeUnit.MILLISECONDS.toNanos(CHUNK_MILLISECONDS);

        Thread thread = Thread.ofVirtual().name("audio-feeder-" + camera.name()).start(() -> {
            LinkedBlockingDeque<byte[]> queue = queues.get(camera.name());
            try (FileOutputStream output = new FileOutputStream(camera.audioPipePath())) {
                long nextWrite = System.nanoTime();
                while (running.get()) {
                    while (queue.size() > TARGET_QUEUE_CHUNKS) {
                        queue.pollFirst();
                    }
                    byte[] chunk = queue.pollFirst();
                    output.write(chunk != null ? chunk : silence);
                    output.flush();

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
                    log.warn("[AudioFeeder] Audio pipe closed for {}: {}", camera.name(), e.getMessage());
                }
            }
            log.info("[AudioFeeder] Stopped feeder for {}", camera.name());
        });
        feederThreads.put(camera.name(), thread);
    }

    private void stopFeeder(String cameraName) {
        AtomicBoolean running = feeders.remove(cameraName);
        if (running != null) {
            running.set(false);
        }

        Thread thread = feederThreads.remove(cameraName);
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            Files.deleteIfExists(Path.of("/tmp/" + cameraName.replaceAll("\\s+", "") + "-audio.pipe"));
        } catch (IOException e) {
            log.warn("[AudioFeeder] Failed to remove audio pipe for {}: {}", cameraName, e.getMessage());
        }
    }

    private void startAudioReader(
            CameraProperties camera,
            Process process,
            LinkedBlockingDeque<byte[]> queue
    ) {
        int sampleRate = Integer.parseInt(properties.output().audioSampleRate());
        int chunkSize = sampleRate * BYTES_PER_SAMPLE * CHUNK_MILLISECONDS / 1000;

        Thread.ofVirtual().name("audio-reader-" + camera.name()).start(() -> {
            try {
                while (process.isAlive()) {
                    byte[] chunk = process.getInputStream().readNBytes(chunkSize);
                    if (chunk.length == 0) {
                        break;
                    }
                    if (chunk.length < chunkSize) {
                        chunk = Arrays.copyOf(chunk, chunkSize);
                    }
                    while (!queue.offerLast(chunk)) {
                        queue.pollFirst();
                    }
                }
            } catch (IOException e) {
                if (process.isAlive()) {
                    log.warn("[AudioWorker] Failed reading audio for {}: {}", camera.name(), e.getMessage());
                }
            }
        });
    }

    private void startErrorReader(CameraProperties camera, Process process) {
        Thread.ofVirtual().name("audio-errors-" + camera.name()).start(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[AudioWorker-{}] {}", camera.name(), line);
                }
            } catch (IOException e) {
                if (process.isAlive()) {
                    log.warn("[AudioWorker] Failed reading stderr for {}: {}", camera.name(), e.getMessage());
                }
            }
        });
    }

    private void startExitMonitor(CameraProperties camera, Process process) {
        Thread.ofVirtual().name("audio-exit-" + camera.name()).start(() -> {
            try {
                int exitCode = process.waitFor();
                synchronized (cameraLock(camera.name())) {
                    if (workers.remove(camera.name(), process)) {
                        scheduleWorkerRestart(camera.name());
                        log.warn("[AudioWorker] Worker for {} exited with code {}; using silence",
                                camera.name(), exitCode);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private Object cameraLock(String cameraName) {
        return cameraLocks.computeIfAbsent(cameraName, ignored -> new Object());
    }

    private void scheduleWorkerRestart(String cameraName) {
        nextWorkerStartAt.put(
                cameraName,
                System.currentTimeMillis()
                        + TimeUnit.SECONDS.toMillis(properties.watchdog().cameraRetrySeconds()));
    }
}
