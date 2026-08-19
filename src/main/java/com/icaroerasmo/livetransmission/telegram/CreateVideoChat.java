package com.icaroerasmo.livetransmission.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethodBoolean;
import org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException;

/**
 * Custom implementation of the Telegram Bot API {@code createVideoChat} method.
 * <p>
 * Creates a video chat / live stream in a supergroup, group or channel.
 * Use {@code isRtmp = true} to create an RTMP-based live broadcast that allows
 * pushing a video feed via an external RTMP stream.
 * </p>
 * See <a href="https://core.telegram.org/bots/api#createvideochat">Telegram Bot API – createVideoChat</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CreateVideoChat extends BotApiMethodBoolean {

    public static final String PATH = "createVideoChat";

    @JsonProperty("chat_id")
    private String chatId;

    @JsonProperty("title")
    private String title;

    /** Schedule the video chat to be started in the future (Unix timestamp). */
    @JsonProperty("schedule_date")
    private Integer scheduleDate;

    /**
     * Pass {@code true} to create an RTMP-based live stream instead of a
     * standard video chat.  Only supported for supergroups and channels.
     */
    @JsonProperty("is_rtmp")
    private Boolean isRtmp;

    @Override
    public String getMethod() {
        return PATH;
    }

    @Override
    public void validate() throws TelegramApiValidationException {
        if (chatId == null || chatId.isBlank()) {
            throw new TelegramApiValidationException("chatId must not be blank", this);
        }
    }
}
