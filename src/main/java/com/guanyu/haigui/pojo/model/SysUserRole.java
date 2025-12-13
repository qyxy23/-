package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Immutable;

/**
 * 用户-角色关联实体（对应 sys_user_role 表）
 */
@Data
@Entity
@Table(name = "sys_user_role",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_role",
                        columnNames = {"user_id", "role_id"}
                )
        },
        indexes = {
                @Index(name = "idx_user_id", columnList = "user_id"),
                @Index(name = "idx_role_id", columnList = "role_id")
        })
@Immutable // 表示该实体通常是只读的
public class SysUserRole {

    @EmbeddedId
    private UserRoleId id;

    // 关联的用户实体
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId") // 映射到复合主键的 userId 字段
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UserInfo user;

    // 关联的角色实体（修正为 SysRole 类型）
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId") // 映射到复合主键的 roleId 字段
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private SysRole role; // 修正这里：使用 SysRole 而不是 UserRoleEnum

    /**
     * 复合主键类
     */
    @Data
    @Embeddable
    public static class UserRoleId implements java.io.Serializable {
        @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
        private Long userId;

        @Column(name = "role_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
        private Long roleId;

        // 必须有无参构造函数
        public UserRoleId() {}

        public UserRoleId(Long userId, Long roleId) {
            this.userId = userId;
            this.roleId = roleId;
        }

        // 重写 equals 和 hashCode 方法
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserRoleId that = (UserRoleId) o;
            return userId.equals(that.userId) && roleId.equals(that.roleId);
        }

        @Override
        public int hashCode() {
            return 31 * userId.hashCode() + roleId.hashCode();
        }
    }
}