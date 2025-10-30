package com.guanyu.haigui.pojo.dto;

import lombok.Data;

// 好友申请请求DTO
@Data
public class FriendApplyRequest {
    private Long targetUserId; // 申请的目标用户ID
    private String remark; // 申请备注
}