package com.guanyu.haigui.pojo.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.guanyu.haigui.Enum.ChatType;
import com.guanyu.haigui.Enum.MessageStatus;
import com.guanyu.haigui.Enum.SenderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通用聊天消息实体类（对应chat_messages表）
 * 核心：覆盖群聊/私聊/群聊AI消息，存储关键信息
 */
@Data
@Entity
@Table(
    name = "chat_messages",
    uniqueConstraints = @UniqueConstraint(columnNames = {"chat_type", "chat_id"}) // 私聊对唯一约束
)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ALLChatMessage {

    /** 消息ID（自增主键） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "msg_id")
    private Long msgId;

    /** 聊天类型：GROUP-群聊 / PRIVATE-私聊 */
    @Enumerated(EnumType.STRING) // 存储枚举字符串（如"GROUP"）
    @Column(name = "chat_type", nullable = false)
    private ChatType chatType;

    /** 关联ID：群聊→房间ID / 私聊→用户对标识（如"123_456"） */
    @Column(name = "chat_id", nullable = false, length = 36)
    private String chatId;

    /** 发送者类型：USER-用户 / ASSISTANT-AI / SYSTEM-系统 */
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false)
    private SenderType senderType;

    /** 发送者ID：USER时关联sys_user.user_id（AI/系统时可选） */
    @Column(name = "sender_id")
    private Long senderId;

    /** 接收者ID：仅私聊有效，关联sys_user.user_id */
    @Column(name = "receiver_id")
    private Long receiverId;

    /** 消息内容（支持文本/富文本） */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 消息状态：SENT-已发送 / READ-已读 / RETRACTED-撤回 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MessageStatus status;

    /** 发送时间（精确到微秒，默认当前时间） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") // JSON序列化格式
    @Column(name = "send_time", nullable = false)
    private LocalDateTime sendTime;

    /** 是否已读（0=未读，1=已读） */
    @Column(name = "is_read", nullable = false, columnDefinition = "TINYINT(1)")
    private Integer isRead;

    /** 最后更新时间（记录消息状态变更，如已读） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    // 在ALLChatMessage类中添加以下字段和关联
    @ManyToOne(fetch = FetchType.LAZY) // 懒加载，避免N+1问题
    @JoinColumn(
            name = "sender_id",          // 当前表的sender_id字段
            referencedColumnName = "user_id", // 关联sys_user的user_id
            nullable = true               // 允许为空（AI/系统消息无sender_id）
    )
    private UserInfo sender; // 发送者（用户实体）
}