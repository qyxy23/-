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
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * 群聊消息表
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_group_messages")
public class GroupMessage {

    @Id
    @Column(name = "message_id", length = 36, nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    @Schema(description = "消息唯一ID（UUID）")
    private String messageId;

    /**
     * 所属群聊
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    @Schema(description = "所属群聊")
    private ChatRoom room;

    /**
     * 发送者
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    @Schema(description = "发送者")
    private UserInfo sender;

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

    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    @Schema(description = "消息发送时间")
    private LocalDateTime createTime;
}