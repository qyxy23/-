package com.guanyu.haigui.Enum;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

// 房间状态
@Getter
public enum RoomStatus {
    WAITING("未开始"),
    ACTIVE("进行中"),
    VOTING("进行投票"),
    FINISHED("已结束"),
    CANCELLED("已取消");

    @JsonCreator
    public static RoomStatus fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return WAITING;
        }
        return RoomStatus.valueOf(value.toUpperCase());
    }

    private final String description;

    RoomStatus(String description) {
        this.description = description;
    }
}