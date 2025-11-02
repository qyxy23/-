package com.guanyu.haigui.Enum;

import lombok.Getter;

/**
 * AI聊天会话的上下文类型（对应数据库context_type ENUM）
 */
@Getter
public enum ChatContextType {
    GROUP_CHAT("群聊AI"),
    PRIVATE_CHAT("私聊AI"),
    GAME_ROOM("游戏大厅AI"),
    AI_SOLO("纯AI单独对话");

    private final String description;

    ChatContextType(String description) {
        this.description = description;
    }
}