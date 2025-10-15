package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class SendMessageRequest {
    private String roomId;
    private String content;
}