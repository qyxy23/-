package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * AI聊天会话实体类（对应ai_chat_sessions表）
 */
@Data
@Entity
@Table(name = "ai_chat_sessions",
        indexes = {
                @Index(name = "idx_user_id", columnList = "user_id"),
                @Index(name = "idx_create_time", columnList = "create_time"),
                @Index(name = "idx_ai_chat_sessions_room", columnList = "room_id")
        })
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiChatSession {

    /** 会话唯一ID（UUID，对应数据库session_id） */
    @Id
    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId; // 注意：数据库是VARCHAR(36)，不再是Long！

    /** 发起聊天的用户ID（关联sys_user表） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 会话标题（默认"新对话"） */
    @Column(name = "title", nullable = false)
    @org.hibernate.annotations.ColumnDefault("'新对话'") // 映射数据库默认值
    private String title;

    /** 会话创建时间（数据库默认CURRENT_TIMESTAMP） */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /** 最后一条消息时间（数据库ON UPDATE CURRENT_TIMESTAMP） */
    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 逻辑删除标记（0=未删除，1=已删除） */
    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;

    /** 关联的群聊房间ID（可选，关联chat_rooms表） */
    @Column(name = "room_id", length = 36)
    private String roomId; // 对应数据库VARCHAR(36)

    // 可选：关联ChatRoom实体（如果需要操作群聊信息）
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "room_id", referencedColumnName = "room_id")
    // private ChatRoom chatRoom;
}