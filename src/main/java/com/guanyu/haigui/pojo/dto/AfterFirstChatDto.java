package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class AfterFirstChatDto {
    // 用户发送的聊天消息
    private String message;
    // 聊天室id
    private String roomId;
}
