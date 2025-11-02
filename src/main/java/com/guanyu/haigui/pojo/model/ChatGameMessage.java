package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.MessageStatus;
import com.guanyu.haigui.Enum.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 游戏大厅消息实体（对应chat_game_messages表）
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_game_messages")
public class ChatGameMessage {

    /** 消息ID（UUID，对应message_id） */
    @Id
    @Column(name = "message_id", length = 36, nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID) // 自动生成UUID
    private String messageId;

    /** 所属游戏房间（关联chat_games表） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    @Schema(description = "所属游戏房间")
    private ChatGame chatGame;

    /** 发送者（关联sys_user表） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    @Schema(description = "消息发送者")
    private UserInfo sender;

    /** 消息内容（对应content，LONGTEXT类型） */
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** 消息类型（对应message_type，枚举存储字符串） */
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType;

    /** 消息状态（对应status，枚举存储字符串） */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MessageStatus status;

    /** 发送时间（数据库自动生成，对应create_time） */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    @Schema(description = "消息发送时间")
    private LocalDateTime createTime;
}