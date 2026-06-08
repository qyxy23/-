package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class ClearChatHistoryDTO {
    private String sessionId;
    /** PRIVATE / GROUP */
    private String chatType;
}
