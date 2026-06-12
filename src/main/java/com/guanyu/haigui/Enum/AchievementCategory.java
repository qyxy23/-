package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum AchievementCategory {
    ONBOARDING("初探海龟汤"),
    MILEAGE("喝汤里程"),
    SESSION("单局表现"),
    MULTI("多人协作"),
    UGC("创作社区"),
    TAG("氛围探索"),
    HIDDEN("隐藏彩蛋");

    private final String label;

    AchievementCategory(String label) {
        this.label = label;
    }
}
