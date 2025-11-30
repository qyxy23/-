package com.guanyu.haigui.Enum;

import lombok.Getter;

import java.util.Objects;

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
            if (Objects.equals(tag.description, description)) {
                return tag;
            }
        }
        return OTHER;
    }

    /**
     * 根据枚举名称获取枚举（支持前端传入枚举名称如CREATIVE）
     */
    public static SoupTag fromName(String name) {
        for (SoupTag tag : values()) {
            if (tag.name().equalsIgnoreCase(name)) {
                return tag;
            }
        }
        return OTHER;
    }

    /**
     * 从描述或名称获取枚举（优先匹配名称，再匹配描述）
     */
    public static SoupTag fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return OTHER;
        }

        // 优先按枚举名称匹配（前端传入格式）
        for (SoupTag tag : values()) {
            if (tag.name().equalsIgnoreCase(value.trim())) {
                return tag;
            }
        }

        // 再按描述匹配（中文描述）
        for (SoupTag tag : values()) {
            if (tag.description.equalsIgnoreCase(value.trim())) {
                return tag;
            }
        }

        return OTHER;
    }
}