package com.guanyu.haigui.pojo.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.guanyu.haigui.Enum.ChatContextType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * AI聊天会话实体类（对应ai_chat_sessions表，已适配多场景）
 */
@Data
@Entity
@Table(
        name = "ai_chat_sessions",
        indexes = {
                @Index(name = "idx_user_id", columnList = "user_id"),
                @Index(name = "idx_create_time", columnList = "create_time"),
                @Index(name = "idx_context", columnList = "context_type, context_id") // 新增场景索引
        }
)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiChatSession {

    /** 会话唯一ID（UUID，对应数据库VARCHAR(36)） */
    @Id
    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId; // 修正：原Long改为String（匹配数据库VARCHAR(36)）

    /** 发起聊天的用户ID（关联sys_user.user_id） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 会话标题（默认"新对话"） */
    @Column(name = "title", nullable = false)
    @org.hibernate.annotations.ColumnDefault("'新对话'")
    private String title;

    /** 会话创建时间（数据库默认CURRENT_TIMESTAMP(6)） */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /** 最后更新时间（数据库ON UPDATE CURRENT_TIMESTAMP(6)） */
    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /** 逻辑删除标记（0=未删，1=已删） */
    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;

    /** 🔑 关联场景类型（GROUP_CHAT/PRIVATE_CHAT/GAME_ROOM/AI_SOLO） */
    @Enumerated(EnumType.STRING) // 存储枚举字符串（匹配数据库ENUM）
    @Column(name = "context_type", nullable = false)
    private ChatContextType contextType; // 新增：场景类型枚举

    /** 🔑 关联场景ID（如群聊group_id/游戏room_id/私聊用户ID） */
    @Column(name = "context_id", nullable = false, length = 36)
    private String contextId; // 新增：场景唯一标识

    // 可选：关联ChatRoom实体（若需操作游戏房间信息）
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "room_id", referencedColumnName = "room_id")
    // private ChatRoom chatRoom;
}