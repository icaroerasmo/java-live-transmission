package com.icaroerasmo.livetransmission.model;

import lombok.Data;

@Data
public class Camera {
    private String name;
    private String rtspUrl;
    private String chatId;
}
