package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class getPrivateHistoryMessagesDTO {
    // 接收者ID
    private Long receiverId;
    // 页码
    private Integer page;
    // 页大小
    private Integer size;
}
