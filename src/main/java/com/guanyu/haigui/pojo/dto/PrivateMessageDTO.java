package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.MessageStatus;
import com.guanyu.haigui.Enum.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 历史消息DTO（转换实体类）
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PrivateMessageDTO {
    // 接收者ID
    private Long receiverId;
    // 发送者ID
    private String content;
    // 消息类型
    private MessageType messageType;
    // 消息状态
    private MessageStatus status;
    // 是否已读
    private Boolean isRead;
    // 创建时间
    private LocalDateTime createTime;
}