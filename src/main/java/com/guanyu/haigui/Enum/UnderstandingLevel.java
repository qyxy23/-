package com.guanyu.haigui.Enum;

import lombok.Getter;
// 理解层次枚举（保持不变）
@Getter
public enum UnderstandingLevel {
    LEVEL_1(1, "发现表层事实"),
    LEVEL_2(2, "理解内在联系"),
    LEVEL_3(3, "推理部分真相"),
    LEVEL_4(4, "掌握核心逻辑");

    private final int level;
    private final String description;

    UnderstandingLevel(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public static UnderstandingLevel fromLevel(int level) {
        for (UnderstandingLevel ul : values()) {
            if (ul.level == level) return ul;
        }
        throw new IllegalArgumentException("无效的理解层次: " + level);
    }

}