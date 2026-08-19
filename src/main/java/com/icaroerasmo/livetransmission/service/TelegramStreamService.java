package com.icaroerasmo.livetransmission.service;

import com.icaroerasmo.livetransmission.config.TransmissionProperties;
import com.icaroerasmo.livetransmission.model.Camera;
import com.icaroerasmo.livetransmission.model.RtmpUrl;
import com.icaroerasmo.livetransmission.telegram.CreateVideoChat;
import com.icaroerasmo.livetransmission.telegram.GetVideoChatRtmpUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Service responsible for interacting with the Telegram Bot API to manage
 * live video streams (RTMP-based video chats / live streams).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramStreamService {

    private final TransmissionProperties properties;
    private OkHttpTelegramClient telegramClient;

    private OkHttpTelegramClient getClient() {
        if (telegramClient == null) {
            telegramClient = new OkHttpTelegramClient(properties.getBotToken());
        }
        return telegramClient;
    }

    /**
     * Retrieves the RTMP push URL for the given camera's chat.
     * If no live stream is currently active, one is started first.
     *
     * @param camera the camera whose chatId is used
     * @return full RTMP URL suitable for pushing an ffmpeg stream
     * @throws TelegramApiException when the Telegram API call fails
     */
    public String getRtmpUrl(Camera camera) throws TelegramApiException {
        String chatId = camera.getChatId();
        try {
            GetVideoChatRtmpUrl method = GetVideoChatRtmpUrl.builder()
                    .chatId(chatId)
                    .build();
            RtmpUrl rtmpUrl = getClient().execute(method);
            return rtmpUrl.getFullUrl();
        } catch (TelegramApiException e) {
            log.warn("No active live stream for chat {}; attempting to start one: {}", chatId, e.getMessage());
            startLiveStream(camera);
            GetVideoChatRtmpUrl method = GetVideoChatRtmpUrl.builder()
                    .chatId(chatId)
                    .build();
            RtmpUrl rtmpUrl = getClient().execute(method);
            return rtmpUrl.getFullUrl();
        }
    }

    /**
     * Starts an RTMP-based video chat (live stream) in the given chat.
     *
     * @param camera the camera configuration whose chatId to use
     * @throws TelegramApiException when the Telegram API call fails
     */
    public void startLiveStream(Camera camera) throws TelegramApiException {
        String chatId = camera.getChatId();
        log.info("Starting Telegram live stream for chat {} (camera: {})", chatId, camera.getName());
        CreateVideoChat method = CreateVideoChat.builder()
                .chatId(chatId)
                .title(camera.getName())
                .isRtmp(true)
                .build();
        getClient().execute(method);
        log.info("Telegram live stream started for chat {}", chatId);
    }
}
