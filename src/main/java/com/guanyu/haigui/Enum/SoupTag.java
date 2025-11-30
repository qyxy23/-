package com.guanyu.haigui.Enum;

import lombok.Getter;

/**
 * 海龟汤标签枚举
 */
@Getter
public enum SoupTag {
    /**
     * 惊悚
     */
    HORROR("惊悚", 1),

    /**
     * 欢乐
     */
    HAPPY("欢乐", 2),

    /**
     * 情感
     */
    EMOTIONAL("情感", 3),

    /**
     * 脑洞
     */
    CREATIVE("脑洞", 4),

    /**
     * 奇幻
     */
    FANTASY("奇幻", 5),

    /**
     * 日常
     */
    DAILY("日常", 6),

    /**
     * 其他
     */
    OTHER("其他", 7);

    private final String description;
    private final int code;

    SoupTag(String description, int code) {
        this.description = description;
        this.code = code;
    }

    /**
     * 根据描述获取枚举
     */
    public static SoupTag fromDescription(String description) {
        for (SoupTag tag : values()) {
            if (tag.description.equals(description)) {
                return tag;
            }
        }
        return OTHER;
    }
}