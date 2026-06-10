package com.guanyu.haigui.pojo.result;

import lombok.Data;

@Data
public class ChatWithAIRoomRequest {
    /** 玩家问题 */
    private String question;

    /** 游戏会话 ID（haigui_game_session.session_id） */
    private String gameSessionId;
}
