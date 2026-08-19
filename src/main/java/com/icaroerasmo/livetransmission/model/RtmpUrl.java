package com.icaroerasmo.livetransmission.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * Represents the RTMP URL returned by the Telegram Bot API
 * {@code getVideoChatRtmpUrl} method.
 */
@Data
public class RtmpUrl implements Serializable {

    @JsonProperty("url")
    private String url;

    @JsonProperty("stream_key")
    private String streamKey;

    /**
     * Returns the full RTMP push URL by combining the base URL and stream key.
     */
    public String getFullUrl() {
        return url + "/" + streamKey;
    }
}
