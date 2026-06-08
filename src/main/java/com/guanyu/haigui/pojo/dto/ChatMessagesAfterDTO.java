package com.guanyu.haigui.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessagesAfterDTO {
    /** 私聊：对方用户 ID */
    private Long receiverId;
    /** 群聊：群 ID */
    private String groupId;
    /** 增量起点（不含） */
    private LocalDateTime afterTime;
    private Integer size;
}
