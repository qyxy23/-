package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.MessageType;
import lombok.Data;

/**
 * 群聊消息发送请求（仅群聊相关字段）
 */
@Data
public class SendGroupMessageRequest {
    private String groupId;       // 群ID
    private String content;       // 消息内容
    private MessageType messageType; // 消息类型（TEXT/IMAGE等）
    /** 客户端消息 ID，发送重试幂等 */
    private String clientMsgId;
}