package com.guanyu.haigui.Enum;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

// 房间状态
@Getter
public enum RoomStatus {
    WAITING, // 等待人数集齐
    ACTIVE,  // 已激活（可对话）
    FINISHED, // 已结束
    CANCELLED;

    @JsonCreator
    public static RoomStatus fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return WAITING;
        }
        return RoomStatus.valueOf(value.toUpperCase());
    }
}