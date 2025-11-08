package com.guanyu.haigui.Enum;

import lombok.Getter;

// 聊天消息角色
@Getter
public enum ChatMessageRole {
    USER,
    ASSISTANT,
    SYSTEM;

    /**
     * 转换为第三方库的ChatMessageRole枚举
     */
    public com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole toThirdParty() {
        return switch (this) {
            case USER -> com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole.USER;
            case ASSISTANT -> com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole.ASSISTANT;
            case SYSTEM -> com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole.SYSTEM;
        };
    }
}