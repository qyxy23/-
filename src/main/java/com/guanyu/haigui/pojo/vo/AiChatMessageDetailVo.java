package com.guanyu.haigui.pojo.vo;

import lombok.Data;

@Data
public class AiChatMessageDetailVo {
    // 发送者ID
    private String senderId;
    // 发送者类型
    private String senderType;
    // 消息内容
    private String content;
    // 发送时间
    private String sendTime;
    // 是否已读
    private Boolean isRead;
}
