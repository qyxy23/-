package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Data
@Entity
@Table(name = "chat_group_administrators")
public class ChatGroupAdministrator {

    /** 复合主键（群ID + 管理员用户ID） */
    @EmbeddedId
    private ChatGroupAdministratorId id;

    /**
     * 关联群成员（ChatGroupMember）—— 用@MapsId直接映射复合主键字段
     * 无需@OneToOne或@JoinColumns，简化关联！
     */
    @MapsId("userId") // 映射复合主键的userId字段
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserInfo user; // 对应ChatGroupMember.id.memberId（Long类型）

    @MapsId("groupId") // 映射复合主键的groupId字段
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ChatGroup chatGroup; // 对应ChatGroupMember.id.groupId（String类型）

    /** 是否是群主 */
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isOwner;

    /** 任命时间 */
    @CreationTimestamp
    @Column(nullable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime appointTime;

    // 重写equals和hashCode（基于复合主键）
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ChatGroupAdministrator that = (ChatGroupAdministrator) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}