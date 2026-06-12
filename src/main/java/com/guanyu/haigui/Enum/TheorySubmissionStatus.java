package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum TheorySubmissionStatus {
    LOCKED("未解锁"),
    REJECTED("覆盖不足"),
    PARTIAL("部分正确"),
    WIN("推理成功");

    private final String description;

    TheorySubmissionStatus(String description) {
        this.description = description;
    }
}
