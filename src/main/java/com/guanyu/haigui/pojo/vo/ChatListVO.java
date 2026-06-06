package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatListVO {
    private String roomId;
    private String title;
    private String soupContent;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
    private Integer finalScore;
}