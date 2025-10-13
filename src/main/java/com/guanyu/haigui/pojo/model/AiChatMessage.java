package com.guanyu.haigui.pojo.model;

import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

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
        private ChatMessageRole role;
        private Object content;

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
            // 1. 创建 AiChatMessage 实例（目标类型）
            AiChatMessage msg = new AiChatMessage();

            // 2. 填充父类字段（从子类Builder继承的字段中获取）
            msg.setRole(this.role);               // 来自父类Builder的 role
            msg.setContent(this.content);         // 来自父类Builder的 content

            // 3. 填充子类字段（子类Builder自己的字段）
            msg.setMsgId(this.msgId);
            msg.setSessionId(this.sessionId);
            msg.setSendTime(this.sendTime);
            msg.setIsRead(this.isRead);

            return msg;
        }
    }
}