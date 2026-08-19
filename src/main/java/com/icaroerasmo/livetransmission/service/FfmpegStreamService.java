package com.icaroerasmo.livetransmission.service;

import com.icaroerasmo.livetransmission.config.TransmissionProperties;
import com.icaroerasmo.livetransmission.model.Camera;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that manages ffmpeg processes for each camera, forwarding RTSP streams
 * to Telegram RTMP ingest endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FfmpegStreamService {

    private final TransmissionProperties properties;

    /** Map of camera name -> running ffmpeg process. */
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();

    /**
     * Starts an ffmpeg process that reads from the camera's RTSP URL and pushes
     * the stream to the given RTMP URL.
     *
     * @param camera  the camera to stream
     * @param rtmpUrl the full RTMP push URL provided by Telegram
     * @throws IOException if the ffmpeg process cannot be started
     */
    public void startStream(Camera camera, String rtmpUrl) throws IOException {
        stopStream(camera);

        List<String> command = buildFfmpegCommand(camera.getRtspUrl(), rtmpUrl);
        log.info("Starting ffmpeg for camera '{}': {}", camera.getName(), String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        Process process = pb.start();
        runningProcesses.put(camera.getName(), process);
        log.info("ffmpeg started for camera '{}' (PID unavailable in Java 8 compat mode)", camera.getName());
    }

    /**
     * Stops the running ffmpeg process for the given camera, if any.
     *
     * @param camera the camera whose stream to stop
     */
    public void stopStream(Camera camera) {
        Process process = runningProcesses.remove(camera.getName());
        if (process != null && process.isAlive()) {
            log.info("Stopping ffmpeg for camera '{}'", camera.getName());
            process.destroy();
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for ffmpeg to stop for camera '{}'", camera.getName());
            }
        }
    }

    /**
     * Returns {@code true} if the ffmpeg process for the given camera is still running.
     *
     * @param camera the camera to check
     * @return {@code true} if alive, {@code false} otherwise
     */
    public boolean isRunning(Camera camera) {
        Process process = runningProcesses.get(camera.getName());
        return process != null && process.isAlive();
    }

    /**
     * Stops all running ffmpeg processes. Called on application shutdown.
     */
    public void stopAll() {
        log.info("Stopping all ffmpeg processes...");
        runningProcesses.keySet().forEach(name -> {
            Process p = runningProcesses.remove(name);
            if (p != null && p.isAlive()) {
                p.destroy();
            }
        });
    }

    private List<String> buildFfmpegCommand(String rtspUrl, String rtmpUrl) {
        List<String> cmd = new ArrayList<>();
        cmd.add(properties.getFfmpegPath());
        cmd.add("-loglevel");
        cmd.add(properties.getFfmpegLogLevel());
        cmd.add("-rtsp_transport");
        cmd.add("tcp");
        cmd.add("-i");
        cmd.add(rtspUrl);
        // Re-encode for compatibility: H.264 video, AAC audio
        cmd.add("-vcodec");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("veryfast");
        cmd.add("-tune");
        cmd.add("zerolatency");
        cmd.add("-acodec");
        cmd.add("aac");
        cmd.add("-ar");
        cmd.add("44100");
        cmd.add("-b:a");
        cmd.add("128k");
        cmd.add("-f");
        cmd.add("flv");
        cmd.add(rtmpUrl);
        return cmd;
    }
}
