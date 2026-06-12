package com.guanyu.haigui.Enum;

import lombok.Getter;

/**
 * 海龟汤内容气质：清汤 / 红汤 / 黑汤（仅后台与判题使用，不对玩家选汤页展示）
 */
@Getter
public enum ContentTone {
    /** 清汤：通常无死亡、恐怖负担较低 */
    CLEAR("清汤"),
    /** 红汤：涉及死亡或强悬疑惊悚 */
    RED("红汤"),
    /** 黑汤：重口、极高恐怖或极难烧脑 */
    BLACK("黑汤");

    private final String description;

    ContentTone(String description) {
        this.description = description;
    }
}
