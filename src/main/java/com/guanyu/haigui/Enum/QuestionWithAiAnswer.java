package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum QuestionWithAiAnswer {
    YES("是"),
    NO("不是"),
    PARTIAL("是或不是"),
    UNIMPORTANT("不重要"),
    UNKNOWN("不知道");

    private final String description;

    QuestionWithAiAnswer(String description) {
        this.description = description;
    }
}
