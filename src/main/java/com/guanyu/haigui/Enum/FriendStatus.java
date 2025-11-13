package com.guanyu.haigui.Enum;

import lombok.Getter;

/**
 * 好友关系状态枚举
 */
@Getter
public enum FriendStatus {
    PENDING("申请中"),    // 等待对方确认
    ACCEPTED("已同意"),   // 成为好友
    REJECTED("已拒绝"),   // 申请被驳回
    BLOCKED("已拉黑"),    // 禁止互动
    RETRACTED("已撤回");


    private final String desc;

    FriendStatus(String desc) {
        this.desc = desc;
    }
}