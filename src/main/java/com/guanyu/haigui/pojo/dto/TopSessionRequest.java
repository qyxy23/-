package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class TopSessionRequest {
    private String sessionId; // 会话ID（私聊=对方用户ID，群聊=群ID）
    private String chatType;  // 会话类型（PRIVATE/GROUP）
    private Boolean isSticky; // 是否置顶（可选，默认true）
}
