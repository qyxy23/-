package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatListVO {
    private String roomId;
    /** 单人游玩时使用游戏会话 ID */
    private String gameSessionId;
    /** MULTI | SOLO */
    private String playMode;
    private String title;
    private String soupContent;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
    private Integer finalScore;
}