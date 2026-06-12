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
 * 群聊消息实体（对应chat_group_messages表）
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_group_messages")
@NamedEntityGraph(
        name = "GroupMessage.withChatGroup",
        attributeNodes = @NamedAttributeNode("chatGroup") // 预加载chatGroup
)
public class GroupMessage {

    /** 消息唯一ID（UUID，对应message_id） */
    @Id
    @Column(name = "message_id", length = 36, nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    @Schema(description = "消息唯一ID（UUID）")
    private String messageId;

    @Column(name = "client_msg_id", length = 64)
    @Schema(description = "客户端消息ID，发送重试幂等")
    private String clientMsgId;

    /** 所属群聊（修正：关联chat_groups表的group_id） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    @Schema(description = "所属群聊")
    private ChatGroup chatGroup; // 之前是room，现改为chatGroup

    /** 发送者（关联sys_user表） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    @Schema(description = "发送者")
    private UserInfo sender;

    /** 消息内容（对应content，LONGTEXT类型） */
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    @Schema(description = "消息内容（文本/图片URL/文件路径等）")
    private String content;

    /** 消息类型（对应message_type，枚举存储字符串） */
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    @Schema(description = "消息类型：TEXT/IMAGE/FILE/VOICE/VIDEO")
    private MessageType messageType;

    /** 消息状态（对应status，枚举存储字符串） */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Schema(description = "消息状态：SENT-已发送/FAILED-发送失败/RETRACTED-已撤回")
    private MessageStatus status;

    /** 发送时间（数据库自动生成，对应create_time） */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    @Schema(description = "消息发送时间")
    private LocalDateTime createTime;
}