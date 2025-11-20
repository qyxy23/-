package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MemberStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LobbyMemberVO {
    private Long userId;         // 成员ID（关联sys_user.user_id）
    private String username;     // 用户名
    private String avatar;       // 用户头像URL
    private LocalDateTime joinTime; // 加入大厅的时间
    private Boolean isCreator;   // 是否是房间创建者（房主）
    private MemberStatus status;
}
