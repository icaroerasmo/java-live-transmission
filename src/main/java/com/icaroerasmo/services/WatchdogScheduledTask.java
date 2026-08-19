package com.icaroerasmo.services;

import com.icaroerasmo.enums.MessagesEnum;
import com.icaroerasmo.properties.CameraProperties;
import com.icaroerasmo.properties.LiveTransmissionProperties;
import com.icaroerasmo.storage.CameraStateStorage;
import com.icaroerasmo.util.TelegramUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Log4j2
@Service
public class WatchdogScheduledTask {

    @Autowired
    private LiveTransmissionProperties properties;

    @Autowired
    private FrameWorkerService frameWorkerService;

    @Autowired
    private CompositorService compositorService;

    @Autowired
    private FrameMonitor frameMonitor;

    @Autowired
    private FrameFeederService frameFeederService;

    @Autowired
    private AudioStreamService audioStreamService;

    @Autowired
    private CameraStateStorage cameraStateStorage;

    @Autowired
    private TelegramUtil telegramUtil;

    @Autowired
    private ExecutorService executorService;

    private volatile boolean started;
    private final List<Future<?>> probeFutures = new ArrayList<>();

    @jakarta.annotation.PostConstruct
    public void init() {
        startAll();
    }

    public void startAll() {
        log.info("[Watchdog] Starting all services");

        for (CameraProperties camera : properties.cameras()) {
            generateFallbackImage(camera);
        }

        for (CameraProperties camera : properties.cameras()) {
            frameWorkerService.startWorker(camera);
        }

        for (CameraProperties camera : properties.cameras()) {
            frameFeederService.startFeeder(camera);
            audioStreamService.start(camera);
        }

        compositorService.start();
        started = true;
        log.info("[Watchdog] All services started");
    }

    @Scheduled(fixedDelayString = "1000")
    public void monitor() {
        if (!started) return;

        // Check compositor health
        if (!compositorService.isRunning()) {
            int rtmpFailures = compositorService.getRtmpFailures();
            long delaySeconds = properties.watchdog().restartDelaySeconds();

            if (rtmpFailures > 0) {
                delaySeconds = Math.min(300, delaySeconds * (long) Math.pow(2, Math.min(rtmpFailures, 5)));
                log.warn("[Watchdog] Compositor RTMP failure #{} — backing off {}s", rtmpFailures, delaySeconds);
            } else {
                log.warn("[Watchdog] Compositor is not running, restarting");
            }

            telegramUtil.sendMessage(MessagesEnum.COMPOSITOR_RESTARTING);

            stopAll();
            try {
                Thread.sleep(delaySeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            startAll();
            return;
        }

        // Monitor each camera
        for (CameraProperties camera : properties.cameras()) {
            CameraStateStorage.CameraState state = cameraStateStorage.getOrCreate(camera);

            // If worker is not running, check if it's time to probe
            if (!frameWorkerService.isWorkerAlive(camera.name())) {
                long now = System.currentTimeMillis() / 1000;

                if (state.workerProcess() != null && state.seenFrame()) {
                    // Worker just died - only notify on transition
                    if (state.available()) {
                        state.setAvailable(false);
                        frameWorkerService.stopWorker(camera.name());
                        audioStreamService.stopWorker(camera.name());
                        activateFallbackImage(camera);
                        state.setNextProbeAt(now + properties.watchdog().cameraRetrySeconds());
                        telegramUtil.sendMessage(MessagesEnum.CAMERA_UNAVAILABLE, camera.label(), "worker exited");
                        log.warn("[Watchdog] {} worker exited, showing fallback", camera.name());
                    }
                }

                // Check if it's time to probe for recovery
                if (now >= state.nextProbeAt() && state.nextProbeAt() > 0) {
                    probeCameraAsync(camera, state);
                }
                continue;
            }

            // Worker is running, check frame freshness
            frameMonitor.checkFrameFreshness(state);

            if (frameMonitor.isFrameStale(state)) {
                // Only notify on transition to unavailable
                if (state.available()) {
                    String reason;
                    if (!state.seenFrame()) {
                        reason = "no frames within " + properties.watchdog().startupFrameTimeoutSeconds() + "s";
                    } else if (state.sameFrameSince() != null) {
                        reason = "same frame repeated for " + properties.watchdog().staticFrameSeconds() + "s";
                    } else {
                        reason = "no frames for " + properties.watchdog().frameTimeoutSeconds() + "s";
                    }

                    log.warn("[Watchdog] {} unavailable: {}", camera.name(), reason);
                    state.setAvailable(false);
                    frameWorkerService.stopWorker(camera.name());
                    audioStreamService.stopWorker(camera.name());
                    activateFallbackImage(camera);
                    state.setNextProbeAt(System.currentTimeMillis() / 1000 + properties.watchdog().cameraRetrySeconds());
                    telegramUtil.sendMessage(MessagesEnum.CAMERA_UNAVAILABLE, camera.label(), reason);
                }
            } else {
                // Frame is fresh - mark as available if it wasn't before
                if (!state.available() && state.seenFrame()) {
                    state.setAvailable(true);
                }
                if (state.available() && !audioStreamService.isWorkerAlive(camera.name())) {
                    audioStreamService.restartWorkerIfDue(camera);
                }
            }
        }
    }

    private void probeCameraAsync(CameraProperties camera, CameraStateStorage.CameraState state) {
        probeFutures.removeIf(f -> f.isDone());

        state.setNextProbeAt(System.currentTimeMillis() / 1000 + properties.watchdog().cameraRetrySeconds());
        log.info("[Watchdog] Probing {} for recovery", camera.name());

        Future<?> future = executorService.submit(() -> {
            try {
                boolean available = probeCamera(camera);
                if (available) {
                    log.info("[Watchdog] {} is available again", camera.name());
                    state.setAvailable(true);
                    frameWorkerService.startWorker(camera);
                    audioStreamService.startWorker(camera);
                } else {
                    log.info("[Watchdog] {} is still unavailable", camera.name());
                }
            } catch (Exception e) {
                log.error("[Watchdog] Error probing {}", camera.name(), e);
            }
        });
        probeFutures.add(future);
    }

    private boolean probeCamera(CameraProperties camera) {
        try {
            List<String> command = List.of(
                    "ffprobe",
                    "-v", "error",
                    "-rtsp_transport", "tcp",
                    "-timeout", camera.rtspTimeoutUs(),
                    "-select_streams", "v:0",
                    "-read_intervals", "%+#1",
                    "-show_entries", "frame=key_frame",
                    "-of", "csv=p=0",
                    camera.rtspUrl()
            );

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            Thread.startVirtualThread(() -> {
                try {
                    process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
                } catch (java.io.IOException e) {
                    if (process.isAlive()) {
                        log.debug("[Watchdog] Probe output failed for {}: {}", camera.name(), e.getMessage());
                    }
                }
            });

            boolean finished = process.waitFor(properties.watchdog().cameraProbeTimeoutSeconds(),
                    java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("[Watchdog] Probe failed for {}: {}", camera.name(), e.getMessage());
            return false;
        }
    }

    private void generateFallbackImage(CameraProperties camera) {
        try {
            String panelWidth = properties.panel().width();
            String panelHeight = properties.panel().height();

            String assContent = String.format("""
                    [Script Info]
                    ScriptType: v4.00+
                    PlayResX: %s
                    PlayResY: %s

                    [V4+ Styles]
                    Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
                    Style: Offline,Adwaita Sans,36,&H00FFFFFF,&H00FFFFFF,&H00000000,&H99000000,-1,0,0,0,100,100,0,0,3,10,0,5,20,20,20,1

                    [Events]
                    Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                    Dialogue: 0,0:00:00.00,9:59:59.00,Offline,,0,0,0,,%s\\NCAMERA IS UNAVAILABLE
                    """, panelWidth, panelHeight, camera.label());

            java.nio.file.Files.writeString(java.nio.file.Path.of(camera.subtitlePath()), assContent);

            List<String> command = List.of(
                    "ffmpeg", "-hide_banner", "-loglevel", "error",
                    "-f", "lavfi", "-i",
                    "smptebars=size=" + panelWidth + "x" + panelHeight + ":rate=1",
                    "-vf", "subtitles=" + camera.subtitlePath() + ":fontsdir=/usr/share/fonts",
                    "-frames:v", "1", "-q:v", "3", "-y",
                    camera.fallbackPath()
            );

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();

            activateFallbackImage(camera);
            log.info("[Watchdog] Generated fallback image for {}", camera.name());
        } catch (Exception e) {
            log.error("[Watchdog] Failed to generate fallback image for {}", camera.name(), e);
        }
    }

    private void activateFallbackImage(CameraProperties camera) {
        try {
            java.nio.file.Path fallback = java.nio.file.Path.of(camera.fallbackPath());
            java.nio.file.Path current = java.nio.file.Path.of(camera.currentPath());
            java.nio.file.Path temp = java.nio.file.Path.of(camera.currentPath() + ".fallback");

            if (java.nio.file.Files.exists(fallback)) {
                java.nio.file.Files.copy(fallback, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                java.nio.file.Files.move(temp, current, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (Exception e) {
            log.error("[Watchdog] Failed to activate fallback image for {}", camera.name(), e);
        }
    }

    public void stopAll() {
        started = false;
        probeFutures.forEach(f -> f.cancel(true));
        probeFutures.clear();
        compositorService.stop();
        frameFeederService.stopAll();
        audioStreamService.stopAll();
        frameWorkerService.stopAll();
    }
}
