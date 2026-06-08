package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户聊天会话索引（Inbox），用于会话列表排序与分页
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "user_chat_session",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_session",
                columnNames = {"user_id", "session_id", "chat_type"}
        )
)
public class UserChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "chat_type", nullable = false, length = 16)
    private String chatType;

    @Column(name = "chat_name", length = 128)
    private String chatName;

    @Column(name = "chat_avatar", length = 512)
    private String chatAvatar;

    @Column(name = "last_message_content", columnDefinition = "TEXT")
    private String lastMessageContent;

    @Column(name = "last_message_time")
    private LocalDateTime lastMessageTime;

    @Column(name = "last_sender_name", length = 128)
    private String lastSenderName;

    @Column(name = "unread_count", nullable = false)
    @Builder.Default
    private Long unreadCount = 0L;

    /** 已读游标：该用户在此会话读到的最后一条消息时间 */
    @Column(name = "read_up_to_time")
    private LocalDateTime readUpToTime;

    /** 已读游标：该用户在此会话读到的最后一条消息 ID */
    @Column(name = "last_read_message_id", length = 36)
    private String lastReadMessageId;

    /** 清空聊天记录边界（账号级，不删服务端消息） */
    @Column(name = "history_clear_at")
    private LocalDateTime historyClearAt;

    @Column(name = "history_clear_message_id", length = 36)
    private String historyClearMessageId;

    @Column(name = "is_sticky", nullable = false)
    @Builder.Default
    private Boolean isSticky = false;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
