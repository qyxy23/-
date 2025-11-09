package com.guanyu.haigui.pojo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/**
 * 群管理员表 - 复合主键
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Embeddable // 标注为嵌入型主键
public class ChatGroupAdministratorId implements Serializable {

    /**
     * 群ID（关联chat_groups.group_id）
     */
    @Column(name = "group_id", length = 36)
    private String groupId;

    /**
     * 管理员用户ID（关联sys_user.user_id）
     */
    @Column(name = "user_id")
    private Long userId;

    // 必须重写equals和hashCode（JPA复合主键要求）
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatGroupAdministratorId that = (ChatGroupAdministratorId) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }
}