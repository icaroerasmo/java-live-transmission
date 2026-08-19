package com.icaroerasmo.livetransmission.service;

import com.icaroerasmo.livetransmission.config.TransmissionProperties;
import com.icaroerasmo.livetransmission.model.Camera;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler that periodically checks each configured camera's stream and
 * (re-)starts the ffmpeg process when needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamScheduler {

    private final TransmissionProperties properties;
    private final TelegramStreamService telegramStreamService;
    private final FfmpegStreamService ffmpegStreamService;

    /**
     * Runs every {@code transmission.ffmpeg-restart-delay-seconds} seconds.
     * For each configured camera, if the ffmpeg process is not alive the stream
     * is restarted.
     */
    @Scheduled(fixedDelayString = "#{${transmission.ffmpeg-restart-delay-seconds:5} * 1000}")
    public void maintainStreams() {
        for (Camera camera : properties.getCameras()) {
            if (!ffmpegStreamService.isRunning(camera)) {
                log.info("Stream for camera '{}' is not running; (re-)starting...", camera.getName());
                try {
                    String rtmpUrl = telegramStreamService.getRtmpUrl(camera);
                    ffmpegStreamService.startStream(camera, rtmpUrl);
                } catch (Exception e) {
                    log.error("Failed to start stream for camera '{}': {}", camera.getName(), e.getMessage(), e);
                }
            }
        }
    }

    @PreDestroy
    public void onShutdown() {
        ffmpegStreamService.stopAll();
    }
}
