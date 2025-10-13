package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.guanyu.haigui.Enum.SenderTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天室列表DTO（用于返回给前端）
 */
@Data
public class ChatRoomListVO {
    /** 会话ID（唯一标识） */
    private String sessionId;

    /** 会话标题 */
    private String title;

    /** 最后一条消息内容（可选） */
    private String lastMessageContent;

    /** 最后一条消息发送者类型（USER/AI，可选） */
    private SenderTypeEnum lastMessageSender;

    /** 最后更新时间（即最后一条消息时间） */
    @JsonFormat(
            pattern = "yyyy-MM-dd HH:mm:ss",  // 序列化后的字符串格式
            timezone = "GMT+8"               // 时区（与配置一致）
    )
    private LocalDateTime lastUpdateTime;

    /** 是否已读（可选，标记用户是否看过最后一条消息） */
    private Integer isRead;

    // 存储SQL返回的原始值（如"USER"/"AI"）
    private String senderType;

    // Setter for senderType：自动转换lastMessageSender
    public void setSenderType(String senderType) {
        this.senderType = senderType;
        try {
            this.lastMessageSender = SenderTypeEnum.valueOf(senderType);
        } catch (IllegalArgumentException e) {
            this.lastMessageSender = null; // 或设置为默认值
        }
    }

}