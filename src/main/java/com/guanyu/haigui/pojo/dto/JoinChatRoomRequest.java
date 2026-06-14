package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class JoinChatRoomRequest {
    private String chatRoomId;
    /** 分享进私密房时的令牌（可选） */
    private String shareToken;
}