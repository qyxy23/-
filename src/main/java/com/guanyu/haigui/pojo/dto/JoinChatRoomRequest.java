package com.guanyu.haigui.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// 请求/消息的DTO
@Data
@AllArgsConstructor
public class JoinChatRoomRequest {
    private String chatRoomId;
}