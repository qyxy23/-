package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.FriendStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 好友信息DTO
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FriendInfoVO {
    // 好友id
    private Long friendId;
    // 用户名
    private String username;
    // 头像
    private String avatar;
    // 好友状态
    private FriendStatus status; // 好友状态
    // 手机号
    private String phone;
    // 邮箱
    private String email;
    // 创建时间
    private LocalDateTime createTime;
}