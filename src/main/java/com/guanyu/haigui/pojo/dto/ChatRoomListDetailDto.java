package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class ChatRoomListDetailDto {
    private String roomId;
    /** 单人游玩复盘时使用 */
    private String gameSessionId;
}
