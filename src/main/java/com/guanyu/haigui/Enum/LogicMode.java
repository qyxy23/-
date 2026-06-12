package com.guanyu.haigui.Enum;

import lombok.Getter;

/**
 * 海龟汤逻辑框架：本格 / 变格（仅后台与判题使用，不对玩家选汤页展示）
 */
@Getter
public enum LogicMode {
    /** 本格：现实逻辑可解释 */
    ORTHODOX("本格"),
    /** 变格：含超自然、科幻、灵异等 */
    VARIANT("变格");

    private final String description;

    LogicMode(String description) {
        this.description = description;
    }
}
