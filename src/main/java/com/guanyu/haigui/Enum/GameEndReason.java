package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum GameEndReason {
    QUESTIONS_EXHAUSTED("提问次数用尽"),
    MANUAL_GIVE_UP("主动放弃"),
    VOTE_PASSED("投票通过结束"),
    ROOM_DISBANDED("房间解散"),
    GUESS_CORRECT("推理正确");

    private final String description;

    GameEndReason(String description) {
        this.description = description;
    }
}
