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
    private Long receiverId;
    private String content;
    private MessageType messageType;
    private MessageStatus status;
    private Boolean isRead;
    private LocalDateTime createTime;
}