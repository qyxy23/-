package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum PlayMode {
    MULTI("多人大厅"),
    SOLO("单人AI");

    private final String description;

    PlayMode(String description) {
        this.description = description;
    }
}
