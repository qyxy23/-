package com.guanyu.haigui.pojo.model;

import lombok.*;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;

import java.time.LocalDateTime;

/**
 * AI聊天消息实体类（对应ai_chat_messages表）
 * 说明：字段名与数据库列名一致（或通过MyBatis结果映射转换）
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessage extends ChatMessage{

    /** 消息ID（自增，对应数据库msg_id） */
    private Long msgId;

    /** 关联会话ID（对应数据库session_id） */
    private Long sessionId;

    /** 消息发送时间（对应数据库send_time） */
    private LocalDateTime sendTime;

    /** 是否已读（0=未读，1=已读，对应数据库is_read） */
    private Integer isRead;
    // /** 发送者类型（USER/AI，对应数据库sender_type） */
    // private SenderTypeEnum senderType;
    //
    // /** 消息内容（文本/富媒体JSON，对应数据库content） */
    // private String content;

    // 手动编写 Builder，继承父类的 ChatMessageBuilder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatMessage.Builder { // 继承父类的 Builder
        private Long msgId;
        private Long sessionId;
        private LocalDateTime sendTime;
        private Integer isRead;

        // 子类字段的 setter 方法
        public Builder msgId(Long msgId) {
            this.msgId = msgId;
            return this;
        }

        public Builder sessionId(Long sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder sendTime(LocalDateTime sendTime) {
            this.sendTime = sendTime;
            return this;
        }

        public Builder isRead(Integer isRead) {
            this.isRead = isRead;
            return this;
        }

        // 重写 build() 方法，设置子类和父类字段
        @Override
        public AiChatMessage build() {
            AiChatMessage msg = (AiChatMessage) super.build(); // 先构建父类部分
            msg.setMsgId(this.msgId);
            msg.setSessionId(this.sessionId);
            msg.setSendTime(this.sendTime);
            msg.setIsRead(this.isRead);
            return msg;
        }
    }
}