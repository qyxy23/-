package com.guanyu.haigui.Enum;

// 房间状态
public enum RoomStatus {
    WAITING, // 等待人数集齐
    ACTIVE,  // 已激活（可对话）
    FINISHED, // 已结束
    CANCELLED
}