package com.guanyu.haigui.pojo.result;

import lombok.Data;

@Data
public class ChatWithAIRoomRequest {
    /**
     * 玩家问题
     */
    private String question;

    /**
     * 房间ID
     */
    private String roomId;
}
