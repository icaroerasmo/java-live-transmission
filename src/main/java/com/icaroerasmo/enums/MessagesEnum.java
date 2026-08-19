package com.icaroerasmo.enums;

public enum MessagesEnum {
    COMPOSITOR_STARTED("compositor.started"),
    COMPOSITOR_STOPPED("compositor.stopped"),
    COMPOSITOR_RESTARTING("compositor.restarting"),
    CAMERA_STARTED("camera.started"),
    CAMERA_STOPPED("camera.stopped"),
    CAMERA_UNAVAILABLE("camera.unavailable"),
    CAMERA_RECOVERED("camera.recovered"),
    CAMERA_PROBING("camera.probing"),
    CAMERA_STATIC_FRAME("camera.static.frame"),
    CAMERA_NO_FRAMES("camera.no.frames"),
    CAMERA_MAX_RESTARTS("camera.max.restarts");

    private final String key;

    MessagesEnum(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
