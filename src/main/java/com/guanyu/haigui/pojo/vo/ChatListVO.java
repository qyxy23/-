package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatListVO {
    private String roomId;      // 房间ID
    private String title;          // 房间标题
    private String soupContent;    // 汤面内容（替换原来的lastMessageContent）
    private LocalDateTime createTime; // 添加创建时间用于排序
}