package com.guanyu.haigui.pojo.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
class ChatMessage {
    private String sender;
    private String content;
    // 可选：时间戳、类型（文本/图片等）
}