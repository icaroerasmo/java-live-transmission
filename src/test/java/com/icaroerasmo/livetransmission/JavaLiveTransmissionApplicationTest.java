package com.icaroerasmo.livetransmission;

import com.icaroerasmo.livetransmission.model.RtmpUrl;
import com.icaroerasmo.livetransmission.telegram.CreateVideoChat;
import com.icaroerasmo.livetransmission.telegram.GetVideoChatRtmpUrl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavaLiveTransmissionApplicationTest {

    @Test
    void rtmpUrlConcatenation() {
        RtmpUrl rtmpUrl = new RtmpUrl();
        rtmpUrl.setUrl("rtmp://dc5.rtmp.t.me/s");
        rtmpUrl.setStreamKey("abc123secret");
        assertEquals("rtmp://dc5.rtmp.t.me/s/abc123secret", rtmpUrl.getFullUrl());
    }

    @Test
    void createVideoChatMethodName() {
        CreateVideoChat method = CreateVideoChat.builder()
                .chatId("-1001234567890")
                .title("Test Camera")
                .isRtmp(true)
                .build();
        assertEquals("createVideoChat", method.getMethod());
        assertDoesNotThrow(method::validate);
    }

    @Test
    void getVideoChatRtmpUrlMethodName() {
        GetVideoChatRtmpUrl method = GetVideoChatRtmpUrl.builder()
                .chatId("-1001234567890")
                .build();
        assertEquals("getVideoChatRtmpUrl", method.getMethod());
        assertDoesNotThrow(method::validate);
    }

    @Test
    void createVideoChatValidationFailsWithoutChatId() {
        CreateVideoChat method = CreateVideoChat.builder().build();
        assertThrows(Exception.class, method::validate);
    }
}
