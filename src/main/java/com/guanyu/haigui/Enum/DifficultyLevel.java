package com.guanyu.haigui.Enum;

import lombok.Getter;

/**
 * 海龟汤难度等级枚举
 */
@Getter
public enum DifficultyLevel {
    /**
     * 入门
     */
    BEGINNER("入门", 1),

    /**
     * 中等
     */
    INTERMEDIATE("中等", 2),

    /**
     * 困难
     */
    ADVANCED("困难", 3);

    private final String description;
    private final int level;

    DifficultyLevel(String description, int level) {
        this.description = description;
        this.level = level;
    }

}