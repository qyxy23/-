package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlayAccessRequestVO {
    private Long requestId;
    private Long userId;
    private String username;
    private String status;
    private String userMessage;
    private String adminNote;
    private Long reviewerId;
    private String reviewerNickname;
    private Integer grantedGames;
    private LocalDateTime reviewedAt;
    private LocalDateTime createTime;
    /** 申请人累计已完成/放弃对局数（审核参考） */
    private Long totalPlayedSessions;
}
