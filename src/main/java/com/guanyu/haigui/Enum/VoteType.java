package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum VoteType {
    END_GAME("提前结束对局"),
    THEORY_SUBMIT("提交推理");

    private final String description;

    VoteType(String description) {
        this.description = description;
    }
}
