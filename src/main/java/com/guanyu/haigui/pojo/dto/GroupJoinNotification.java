package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class GroupJoinNotification {
    private Long requestId; // 申请ID
    private String applicantName; // 申请人用户名
    private String groupId; // 群ID
    private String description; // 加群描述
}