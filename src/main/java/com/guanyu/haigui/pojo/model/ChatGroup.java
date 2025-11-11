package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 普通群聊实体（对应chat_groups表）
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_groups")
public class ChatGroup {

    /** 群聊唯一ID（UUID，对应group_id） */
    @Id
    @Column(name = "group_id", length = 36, nullable = false)
    private String groupId;

    /** 群聊名称（对应group_name） */
    @Column(name = "group_name", nullable = false)
    private String groupName;

    /** 创建者（关联sys_user表，对应creator_id） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private UserInfo creator;

    /** 群头像URL（对应group_avatar，默认空字符串） */
    @Column(name = "group_avatar", columnDefinition = "VARCHAR(255) DEFAULT ''")
    private String groupAvatar;

    /** 创建时间（数据库自动生成，对应create_time） */
    @CreationTimestamp
    @Column(name = "create_time", updatable = false, nullable = false)
    private LocalDateTime createTime;

    /** 更新时间（数据库自动更新，对应update_time） */
    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;


    /** 群成员列表（一对多关联chat_group_members表，级联删除） */
    @OneToMany(mappedBy = "chatGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ChatGroupMember> members = new HashSet<>();

    /** 群置顶列表（一对多关联user_group_sticky表，级联删除） */
    @OneToMany(mappedBy = "chatGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserGroupSticky> stickies = new HashSet<>();

    // ✅ 新增：群管理员列表（一对多关联chat_group_administrators表，级联删除）
    @OneToMany(mappedBy = "chatGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ChatGroupAdministrator> administrators = new HashSet<>();
}