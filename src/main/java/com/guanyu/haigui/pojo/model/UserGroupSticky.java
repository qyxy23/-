package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 群聊置顶实体（对应user_group_sticky表）
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user_group_sticky")
public class UserGroupSticky {

    /** 复合主键（user_id + group_id） */
    @EmbeddedId
    private UserGroupStickyId id;

    /** 是否置顶（0-否，1-是，对应is_sticky） */
    @Column(name = "is_sticky", nullable = false)
    private Boolean isSticky;

    /** 置顶时间（数据库自动生成，对应sticky_time） */
    @CreationTimestamp
    @Column(name = "sticky_time", nullable = false)
    private LocalDateTime stickyTime;

    /** 用户（关联sys_user表） */
    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserInfo user;

    /** 群聊（关联chat_groups表） */
    @MapsId("groupId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ChatGroup chatGroup;
}