package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 群聊成员实体（对应chat_group_members表）
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_group_members")
public class ChatGroupMember {

    /** 复合主键（member_id + group_id） */
    @EmbeddedId
    private ChatGroupMemberId id;

    /** 加入时间（对应join_time） */
    @Column(name = "join_time", nullable = false)
    private LocalDateTime joinTime;

    /** 成员用户（关联sys_user表） */
    @MapsId("memberId") // 映射复合主键的memberId
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private UserInfo member;

    /** 所属群聊（关联chat_groups表） */
    @MapsId("groupId") // 映射复合主键的groupId
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ChatGroup chatGroup;
}