package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum JoinGroupStatus {
    AGREE("已同意"),
    REFUSE("已拒绝");

    private final String description;

    JoinGroupStatus(String description) {
        this.description = description;
    }

}
