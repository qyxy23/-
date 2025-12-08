package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum LobbyMemberStatus{
    JOIN("加入"),
    QUIT("退出"),
    ONLINE("在线"),
    READY("已准备"),
    SUSPEND("挂起"),
    OFFLINE("离线"),
    BECOME_OWNER("转移房主");

    private final String description;

    LobbyMemberStatus(String description) {
        this.description = description;
    }
}
