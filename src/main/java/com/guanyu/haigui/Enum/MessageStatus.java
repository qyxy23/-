package com.guanyu.haigui.Enum;

import lombok.Getter;


/**
 * 消息状态枚举（通用）
 */
@Getter
public enum MessageStatus {
    SENT("已发送"),
    FAILED("发送失败"),
    RETRACTED("已撤回");

    private final String desc;

    MessageStatus(String desc) {
        this.desc = desc;
    }
}