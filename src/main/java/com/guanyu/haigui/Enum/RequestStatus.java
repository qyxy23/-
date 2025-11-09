package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum RequestStatus {
    PENDING("待处理"),
    ACCEPTED("已同意"),
    REJECTED("已拒绝");

    private final String description;

    RequestStatus(String description) {
        this.description = description;
    }

}