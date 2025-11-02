package com.guanyu.haigui.pojo.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 群聊消息已读记录实体（对应chat_group_message_reads表）
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_group_message_reads")
public class GroupMessageRead {

    /** 复合主键（message_id + member_id） */
    @EmbeddedId
    private GroupMessageReadId id;

    /** 已读成员（关联UserInfo） */
    @MapsId("memberId") // 映射复合主键的memberId
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    @Schema(description = "已读成员")
    private UserInfo member;

    /** 关联的消息（关联GroupMessage） */
    @MapsId("messageId") // 映射复合主键的messageId
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    @Schema(description = "关联的消息")
    private GroupMessage groupMessage;

    /** 已读时间（数据库自动生成，对应read_time） */
    @CreationTimestamp
    @Column(name = "read_time", nullable = false)
    @Schema(description = "已读时间")
    private LocalDateTime readTime;
}