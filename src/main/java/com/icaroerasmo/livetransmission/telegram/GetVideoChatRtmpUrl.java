package com.icaroerasmo.livetransmission.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.icaroerasmo.livetransmission.model.RtmpUrl;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException;

/**
 * Custom implementation of the Telegram Bot API {@code getVideoChatRtmpUrl} method.
 * <p>
 * Returns the RTMP URL and stream key that can be used to push a live video
 * stream into an active RTMP-based video chat created by {@link CreateVideoChat}.
 * </p>
 * See <a href="https://core.telegram.org/bots/api#getvideochatrtmpurl">Telegram Bot API – getVideoChatRtmpUrl</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class GetVideoChatRtmpUrl extends BotApiMethod<RtmpUrl> {

    public static final String PATH = "getVideoChatRtmpUrl";

    @JsonProperty("chat_id")
    private String chatId;

    @Override
    public String getMethod() {
        return PATH;
    }

    @Override
    public RtmpUrl deserializeResponse(String answer) throws TelegramApiRequestException {
        return deserializeResponse(answer, RtmpUrl.class);
    }

    @Override
    public void validate() throws TelegramApiValidationException {
        if (chatId == null || chatId.isBlank()) {
            throw new TelegramApiValidationException("chatId must not be blank", this);
        }
    }
}
