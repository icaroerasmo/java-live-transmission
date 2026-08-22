package com.icaroerasmo.properties;

public record CameraProperties(
        String name,
        String label,
        String rtspUrl,
        String threadQueueSize,
        String rtspTimeoutUs,
        String probesize,
        String analyzeduration,
        String filterFps
) {
    public String currentPath() {
        return "/tmp/" + name.replaceAll("\\s+", "") + "-current.jpg";
    }

    public String fallbackPath() {
        return "/tmp/" + name.replaceAll("\\s+", "") + "-fallback.jpg";
    }

    public String subtitlePath() {
        return "/tmp/" + name.replaceAll("\\s+", "") + "-camera-unavailable.ass";
    }

    public String pipePath() {
        return "/tmp/" + name.replaceAll("\\s+", "") + "-frames.pipe";
    }

    public String audioPipePath() {
        return "/tmp/" + name.replaceAll("\\s+", "") + "-audio.pipe";
    }

    public String borderPipePath() {
        return "/tmp/" + name.replaceAll("\\s+", "") + "-border.pipe";
    }
}
