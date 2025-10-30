package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MessageStatus;
import com.guanyu.haigui.Enum.MessageType;
import com.guanyu.haigui.pojo.model.PrivateMessage;
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
public class PrivateMessageVO {
    private String messageId;
    private Long senderId;
    private Long receiverId;
    private String content;
    private MessageType messageType;
    private MessageStatus status;
    private Boolean isRead;
    private LocalDateTime createTime;

    // 从实体类转换
    public static PrivateMessageVO fromEntity(PrivateMessage message) {
        return PrivateMessageVO.builder()
                .messageId(message.getMessageId())
                .senderId(message.getSender().getUserId())
                .receiverId(message.getReceiver().getUserId())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .status(message.getStatus())
                .isRead(message.getIsRead())
                .createTime(message.getCreateTime())
                .build();
    }
}