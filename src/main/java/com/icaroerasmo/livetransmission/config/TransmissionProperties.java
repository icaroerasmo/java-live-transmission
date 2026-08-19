package com.icaroerasmo.livetransmission.config;

import com.icaroerasmo.livetransmission.model.Camera;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "transmission")
public class TransmissionProperties {

    private String botToken;
    private String botUsername;
    private List<Camera> cameras = new ArrayList<>();
    private int ffmpegRestartDelaySeconds = 5;
    private String ffmpegPath = "ffmpeg";
    private String ffmpegLogLevel = "warning";
}
