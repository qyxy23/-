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
 * 群消息已读记录表（记录每个成员对每条群消息的已读时间）
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_group_message_reads")
public class GroupMessageRead {

    /**
     * 复合主键（消息ID + 成员ID）
     */
    @EmbeddedId
    @Schema(hidden = true) // Swagger中隐藏复合主键
    private ChatGroupMessageReadId id;

    /**
     * 已读成员（关联用户）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("memberId") // 映射复合主键中的memberId
    @JoinColumn(name = "member_id", nullable = false)
    @Schema(description = "已读成员")
    private UserInfo member;

    @CreationTimestamp
    @Column(name = "read_time", nullable = false)
    @Schema(description = "已读时间")
    private LocalDateTime readTime;
}