package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.FriendStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 好友信息DTO
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FriendInfoVO {
    private Long friendId;
    private String nickname;
    private String avatar;
    private String remark; // 申请时的备注
    private FriendStatus status; // 好友状态
    private Long unreadCount; // 未读消息数
}