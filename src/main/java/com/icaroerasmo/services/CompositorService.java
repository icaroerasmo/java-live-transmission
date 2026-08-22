package com.icaroerasmo.services;

import com.icaroerasmo.enums.MessagesEnum;
import com.icaroerasmo.messaging.NotificationPublisher;
import com.icaroerasmo.parsers.CompositorCommandParser;
import com.icaroerasmo.properties.LiveTransmissionProperties;
import com.icaroerasmo.runners.FfmpegRunner;
import com.icaroerasmo.storage.DetectionStateStorage;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Log4j2
@Service
public class CompositorService {

    @Autowired
    private LiveTransmissionProperties properties;

    @Autowired
    private NotificationPublisher notificationPublisher;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private DetectionStateStorage detectionStateStorage;

    private FfmpegRunner runner;
    private volatile boolean running;
    private volatile int lastExitCode = 0;
    private volatile int rtmpFailures = 0;

    private static final int RTMP_BROKEN_PIPE_EXIT_CODE = 224;

    public synchronized void start() {
        stop();

        detectionStateStorage.ensureLabelFile();
        runner = new FfmpegRunner("compositor");
        List<String> command = CompositorCommandParser.build(properties, detectionStateStorage.activeCameras());

        log.info("[Compositor] Starting compositor");
        Process process = runner.start(command);

        if (process != null) {
            running = true;
            notificationPublisher.publish(MessagesEnum.COMPOSITOR_STARTED);
            log.info("[Compositor] Compositor started with pid {}", process.pid());

            CompletableFuture.runAsync(() -> {
                try {
                    int exitCode = process.waitFor();
                    running = false;
                    lastExitCode = exitCode;
                    log.warn("[Compositor] Compositor exited with code {}", exitCode);

                    if (exitCode == RTMP_BROKEN_PIPE_EXIT_CODE) {
                        rtmpFailures++;
                        log.warn("[Compositor] RTMP Broken pipe (failure #{})", rtmpFailures);
                        notificationPublisher.publish(MessagesEnum.COMPOSITOR_STOPPED, "RTMP connection dropped");
                    } else {
                        rtmpFailures = 0;
                        notificationPublisher.publish(MessagesEnum.COMPOSITOR_STOPPED, "exit code: " + exitCode);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executorService);
        } else {
            log.error("[Compositor] Failed to start compositor");
            notificationPublisher.publish(MessagesEnum.COMPOSITOR_STOPPED, "failed to start");
        }
    }

    public synchronized void stop() {
        if (runner != null) {
            runner.destroy();
            runner = null;
        }
        running = false;
    }

    public boolean isRunning() {
        return running && runner != null && runner.isAlive();
    }

    public int getRtmpFailures() {
        return rtmpFailures;
    }

    public void resetRtmpFailures() {
        rtmpFailures = 0;
    }
}
