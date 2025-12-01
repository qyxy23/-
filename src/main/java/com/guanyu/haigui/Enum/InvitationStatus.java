package com.guanyu.haigui.Enum;

import lombok.Getter;

/**
 * 房间邀请状态枚举
 */
@Getter
public enum InvitationStatus {
    PENDING("待接受"),    // 对应数据库 PENDING
    ACCEPTED("已接受"),  // 对应数据库 ACCEPTED
    FINISHED("已结束");   // 对应数据库 EXPIRED

    private final String description;

    InvitationStatus(String description) {
        this.description = description;
    }
}