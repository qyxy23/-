/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.pojo.vo.FriendSearchListVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.security.auth.Subject;
import java.security.Principal;
import java.time.LocalDateTime;

/**
 * @author Guanyu
 */
@Entity
@Table(name = "sys_user")
@Data
@AllArgsConstructor
@NoArgsConstructor
@NamedNativeQuery(  // ✅ 正确位置：实体类上
        name = "UserInfo.findFriendInfoWithMessages", // 命名查询唯一标识
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
                u.nickname AS username,         -- 别名匹配VO的username
                u.avatar AS avatar,             -- 别名匹配VO的avatar
                (SELECT COUNT(*) 
                 FROM chat_private_messages m
                 WHERE m.receiver_id = :currentUserId
                   AND m.sender_id = fi.friendId
                   AND m.is_read = 0) AS unreadCount,  -- 别名匹配VO的unreadCount
                (SELECT m.content
                 FROM chat_private_messages m
                 WHERE (m.sender_id = :currentUserId AND m.receiver_id = fi.friendId)
                    OR (m.sender_id = fi.friendId AND m.receiver_id = :currentUserId)
                 ORDER BY m.create_time DESC
                 LIMIT 1) AS lastMessageContent,  -- 别名匹配VO的lastMessageContent
                (SELECT m.create_time
                 FROM chat_private_messages m
                 WHERE (m.sender_id = :currentUserId AND m.receiver_id = fi.friendId)
                    OR (m.sender_id = fi.friendId AND m.receiver_id = :currentUserId)
                 ORDER BY m.create_time DESC
                 LIMIT 1) AS lastMessageTime     -- 别名匹配VO的lastMessageTime
            FROM FriendIds fi
            INNER JOIN sys_user u ON fi.friendId = u.user_id
            GROUP BY fi.friendId, u.nickname, u.avatar
            """,
        resultSetMapping = "FriendSearchListMapping" // ✅ 关联结果映射
)
@SqlResultSetMapping(  // ✅ 正确位置：实体类上
        name = "FriendSearchListMapping",
        classes = @ConstructorResult(
                targetClass = FriendSearchListVO.class,
                columns = {
                        @ColumnResult(name = "userId", type = Long.class),
                        @ColumnResult(name = "username", type = String.class),
                        @ColumnResult(name = "avatar", type = String.class),
                        @ColumnResult(name = "unreadCount", type = Long.class),
                        @ColumnResult(name = "lastMessageContent", type = String.class),
                        @ColumnResult(name = "lastMessageTime", type = LocalDateTime.class)
                }
        )
)
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
    private String createTime;
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
