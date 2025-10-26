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
 * 私聊消息表（含已读状态）
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_private_messages")
public class PrivateMessage {

    @Id
    @Column(name = "message_id", length = 36, nullable = false)
    @Schema(description = "消息唯一ID（UUID）")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String messageId;

    /**
     * 发送者
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    @Schema(description = "发送者")
    private UserInfo sender;

    /**
     * 接收者
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    @Schema(description = "接收者")
    private UserInfo receiver;

    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    @Schema(description = "消息内容（文本/图片URL/文件路径等）")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    @Schema(description = "消息类型：TEXT/IMAGE/FILE/VOICE/VIDEO")
    private MessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Schema(description = "消息状态：SENT-已发送/FAILED-发送失败/RETRACTED-已撤回")
    private MessageStatus status;

    @Column(name = "is_read", nullable = false)
    @Schema(description = "是否已读（0-未读，1-已读）")
    private Boolean isRead;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    @Schema(description = "消息发送时间")
    private LocalDateTime createTime;
}