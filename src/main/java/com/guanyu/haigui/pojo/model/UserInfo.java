package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.pojo.vo.FriendBasicInfoVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.security.auth.Subject;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * @author Guanyu
 */
@Entity
@Table(name = "sys_user")
@Data
@AllArgsConstructor
@NoArgsConstructor
// 原有的@NamedNativeQuery保留，新增一个仅查基础信息的查询 👇
@NamedNativeQuery(name = "UserInfo.findFriendBasicInfos", // 新查询唯一标识
        query = """
                WITH FriendIds AS (
                    SELECT DISTINCT
                        IF(fr.user_id = :currentUserId, fr.friend_id, fr.user_id) AS friendId
                    FROM friend_relations fr
                    WHERE fr.status = 'ACCEPTED'
                      AND (fr.user_id = :currentUserId OR fr.friend_id = :currentUserId)
                )
                SELECT
                    fi.friendId AS userId,          -- 别名匹配VO的userId
                    u.username AS username,         -- 别名匹配VO的username
                    u.avatar AS avatar              -- 别名匹配VO的avatar
                FROM FriendIds fi
                INNER JOIN sys_user u ON fi.friendId = u.user_id
                """, resultSetMapping = "FriendBasicInfoMapping" // ✅ 关联新的结果映射
)
// 新增基础信息的结果映射 👇
@SqlResultSetMapping(name = "FriendBasicInfoMapping", classes = @ConstructorResult(targetClass = FriendBasicInfoVO.class, columns = {
        @ColumnResult(name = "userId", type = Long.class),
        @ColumnResult(name = "username", type = String.class),
        @ColumnResult(name = "avatar", type = String.class)
}))
public class UserInfo implements Principal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;
    // 用户名
    @Schema(description = "用户名")
    private String username;
    // 密码
    private String password;
    // 手机号
    private String phone;
    // 邮箱
    private String email;
    // 创建时间
    private LocalDateTime createTime;
    // 头像
    @Schema(description = "头像")
    private String avatar;
    // 状态
    private boolean enabled;

    public UserInfo(Long userId) {
        this.userId = userId;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public UserInfo(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    @Override
    public String getName() {
        return username;
    }

    @Override
    public boolean implies(Subject subject) {
        return Principal.super.implies(subject);
    }
}
