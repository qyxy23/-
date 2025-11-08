package com.guanyu.haigui.pojo.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.guanyu.haigui.Enum.ChatMessageRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * AI聊天消息实体类（对应ai_chat_messages表）
 */
@Data
@Entity
@Table(
        name = "ai_chat_messages",
        indexes = {
                @Index(name = "idx_session_time", columnList = "session_id, send_time"),
                @Index(name = "idx_sender_time", columnList = "sender_id, send_time")
        }
)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiChatMessage {

    /** 消息ID（自增主键，对应数据库msg_id） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "msg_id")
    private Long msgId;

    /** 关联的AI会话（核心外键：session_id → ai_chat_sessions.session_id） */
    @ManyToOne(fetch = FetchType.LAZY) // 懒加载避免N+1
    @JoinColumn(
            name = "session_id",          // 当前表外键列名
            referencedColumnName = "session_id", // 目标表主键列名
            nullable = false              // 匹配数据库NOT NULL
    )
    private AiChatSession chatSession; // 替代原sessionId（更符合ORM规范）

    /** 发送者类型（USER/ASSISTANT/SYSTEM，对应数据库sender_type ENUM） */
    @Enumerated(EnumType.STRING) // 存储枚举字符串
    @Column(
            name = "sender_type",
            nullable = false
            // columnDefinition = "ENUM('USER', 'ASSISTANT', 'SYSTEM')"  // 强制指定数据库ENUM定义
    )
    private ChatMessageRole role; // 保持与数据库ENUM一致

    /** 发送者ID（用户ID/AI标识，sender_type=USER时必填） */
    @Column(name = "sender_id")
    private Long senderId; // 匹配数据库BIGINT UNSIGNED

    /** 消息内容（TEXT类型，匹配数据库TEXT） */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 消息发送时间（数据库默认CURRENT_TIMESTAMP(6)） */
    @CreationTimestamp // 自动生成创建时间（匹配数据库默认值）
    @Column(name = "send_time", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime sendTime;

    /** 是否已读（0=未读，1=已读，仅用户未读AI消息有效） */
    @Column(name = "is_read", nullable = false)
    private Integer isRead; // 匹配数据库TINYINT(1)

    // 辅助方法：获取会话ID（若需直接访问字符串ID）
    public String getSessionId() {
        return chatSession != null ? chatSession.getSessionId() : null;
    }
}