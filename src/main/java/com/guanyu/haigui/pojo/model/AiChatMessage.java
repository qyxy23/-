package com.guanyu.haigui.pojo.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI聊天消息实体类（对应ai_chat_messages表）
 */
@Data
@Entity
@Table(name = "ai_chat_messages",
        indexes = {
                @Index(name = "idx_session_time", columnList = "session_id, send_time"),
                @Index(name = "idx_sender_time", columnList = "sender_id, send_time")
        })
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiChatMessage {

    /** 消息ID（自增主键，对应数据库msg_id） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "msg_id")
    private Long msgId;

    // 保留sessionId字段（标记为只读）
    // @Column(name = "session_id",length = 36)
    // private String sessionId;

    /** 关联的会话（核心外键：session_id → ai_chat_sessions.session_id） */
    @ManyToOne(fetch = FetchType.LAZY) // 懒加载，避免N+1问题
    @JoinColumn(
            name = "session_id",          // 当前表的外键列名
            referencedColumnName = "session_id", // 目标表的主键列名
            nullable = false              // 对应数据库NOT NULL约束
    )
    private AiChatSession chatSession; // 替代原sessionId字段（更符合ORM规范）

    /** 发送者类型（USER/ASSISTANT/SYSTEM） */
    @Enumerated(EnumType.STRING) // 存储枚举的字符串值（对应数据库ENUM）
    @Column(name = "sender_type", nullable = false)
    private ChatMessageRole role;

    /** 发送者ID（用户ID/AI标识，sender_type=USER时必填） */
    @Column(name = "sender_id")
    private Long senderId;

    /** 消息内容（TEXT类型） */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 消息发送时间（数据库默认CURRENT_TIMESTAMP） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") // JSON序列化格式
    @Column(name = "send_time", nullable = false)
    private LocalDateTime sendTime;

    /** 是否已读（0=未读，1=已读，仅用户未读AI消息时有效） */
    @Column(name = "is_read", nullable = false)
    private Integer isRead;

    public String getSessionId() {
        return chatSession.getSessionId();
    }

    public void setSessionId(String sessionId) {
        this.chatSession.setSessionId(sessionId);
    }

    // 可选：保留sessionId字段（如果需要直接访问字符串ID）
    // @Column(name = "session_id", insertable = false, updatable = false)
    // private String sessionId;
}