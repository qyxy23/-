package com.guanyu.haigui.Enum;

import lombok.Getter;

// 状态枚举类（内部类或独立类均可）


@Getter
public enum MemberStatus {
    ONLINE("在线", "活跃状态，可参与互动"),
    SUSPENDED("挂起", "暂时离开，保留身份但不参与实时互动"),
    READY("已准备", "确认准备，等待游戏开始"),
    OFFLINE("离线", "已退出或断开连接，不参与互动"),
    IN_GAME("游戏中", "正在参与游戏环节（讨论/问答等）");

    private final String desc;
    private final String remark;

    MemberStatus(String desc, String remark) {
        this.desc = desc;
        this.remark = remark;
    }

    // Getter
    public String getDesc() {
        return desc;
    }

    public String getRemark() {
        return remark;
    }
}