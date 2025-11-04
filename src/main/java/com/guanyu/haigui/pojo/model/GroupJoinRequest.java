package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.RequestStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 加群申请记录（记录用户加群请求及处理状态）
 */
@Entity
@Table(name = "group_join_requests")
@Data
public class GroupJoinRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 申请ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserInfo user; // 申请人（关联sys_user）

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ChatGroup group; // 申请加的群（关联chat_groups）

    @Column(length = 500)
    private String description; // 加群描述

    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.PENDING; // 申请状态（默认待处理）

    private LocalDateTime applyTime; // 申请时间

    private LocalDateTime processTime; // 处理时间

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processor_id")
    private UserInfo processor; // 处理人（群主）
}