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

import com.guanyu.haigui.pojo.vo.FriendBasicInfoVO;
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
// 原有的@NamedNativeQuery保留，新增一个仅查基础信息的查询 👇
@NamedNativeQuery(
        name = "UserInfo.findFriendBasicInfos", // 新查询唯一标识
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
            """,
        resultSetMapping = "FriendBasicInfoMapping" // ✅ 关联新的结果映射
)
// 新增基础信息的结果映射 👇
@SqlResultSetMapping(
        name = "FriendBasicInfoMapping",
        classes = @ConstructorResult(
                targetClass = FriendBasicInfoVO.class,
                columns = {
                        @ColumnResult(name = "userId", type = Long.class),
                        @ColumnResult(name = "username", type = String.class),
                        @ColumnResult(name = "avatar", type = String.class)
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
