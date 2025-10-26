package com.guanyu.haigui.Enum;

import lombok.Getter;

/**
 * 消息类型枚举（通用）
 */
@Getter
public enum MessageType {
    TEXT("文本"),
    IMAGE("图片"),
    FILE("文件"),
    VOICE("语音"),
    VIDEO("视频");

    private final String desc;

    MessageType(String desc) {
        this.desc = desc;
    }
}