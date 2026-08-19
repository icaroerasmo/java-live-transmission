package com.icaroerasmo.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("live")
public record LiveTransmissionProperties(
        String rtmpUrl,
        String streamKey,
        OutputProperties output,
        PanelProperties panel,
        InputProperties input,
        WatchdogProperties watchdog,
        java.util.List<CameraProperties> cameras,
        TelegramProperties telegram
) {
    public record OutputProperties(
            String videoBitrate,
            String maxrate,
            String bufsize,
            String fps,
            String gop,
            String width,
            String height,
            String videoCodec,
            String videoPreset,
            String outputTune,
            String audioCodec,
            String audioBitrate,
            String audioSampleRate,
            int audioChannels
    ) {}

    public record PanelProperties(
            String width,
            String height
    ) {}

    public record InputProperties(
            String threadQueueSize,
            String rtspTimeoutUs,
            String probesize,
            String analyzeduration
    ) {}

    public record WatchdogProperties(
            int frameTimeoutSeconds,
            int startupFrameTimeoutSeconds,
            int staticFrameSeconds,
            int cameraRetrySeconds,
            int cameraProbeTimeoutSeconds,
            int restartDelaySeconds
    ) {}
}
