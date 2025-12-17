package com.guanyu.haigui.Enum;

import lombok.Getter;

// 线索类型枚举
@Getter
public enum ClueType {
    TIME("时间"),// 时间
    PLACE("地点"),// 地点
    CHARACTER("人物"),// 角色
    PLOT("情节"),// 情节
    OBJECT("物品"),// 物品
    OTHER("其他");

    private final String desc;

    ClueType(String desc) {
        this.desc = desc;
    }
}