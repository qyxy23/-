package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class GroupChatHistoryDTO {
    private String groupId; // 目标群ID（必传）
    private int page = 0; // 页码（从0开始，默认第一页）
    private int size = 20; // 每页大小（默认10条）
}