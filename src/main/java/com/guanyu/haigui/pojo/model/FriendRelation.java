package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.FriendStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 好友关系表（含申请流程与最终好友列表）
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "friend_relations")
public class FriendRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Schema(description = "主键ID")
    private Long id;

    /**
     * 主动方用户（发起申请/同意后成为好友的主动方）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Schema(description = "主动方用户")
    private UserInfo user;

    /**
     * 被动方用户（接收申请的用户）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_id", nullable = false)
    @Schema(description = "被动方用户")
    private UserInfo friend;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Schema(description = "关系状态：PENDING-申请中/ACCEPTED-已同意/REJECTED-已拒绝/BLOCKED-已拉黑")
    private FriendStatus status;

    @Column(name = "remark")
    @Schema(description = "申请备注（主动方填写的验证信息）")
    private String remark;

    @CreationTimestamp
    @Column(name = "apply_time", nullable = false, updatable = false)
    @Schema(description = "申请发起时间")
    private LocalDateTime applyTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    @Schema(description = "最后状态更新时间（同意/拒绝/拉黑时间）")
    private LocalDateTime updateTime;
}