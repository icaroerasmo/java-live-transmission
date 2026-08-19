package com.icaroerasmo.util;

import com.icaroerasmo.enums.MessagesEnum;

public class TelegramMessageFormatter {

    public static String format(MessagesEnum messageEnum, String message) {
        String prefix = switch (messageEnum) {
            case COMPOSITOR_STARTED, CAMERA_STARTED, CAMERA_RECOVERED -> "🟢 ";
            case COMPOSITOR_STOPPED, CAMERA_STOPPED, CAMERA_UNAVAILABLE,
                 CAMERA_MAX_RESTARTS -> "🔴 ";
            case COMPOSITOR_RESTARTING, CAMERA_PROBING -> "🟡 ";
            case CAMERA_STATIC_FRAME, CAMERA_NO_FRAMES -> "⚠️ ";
        };
        return prefix + escapeHtml(message);
    }

    private static String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
