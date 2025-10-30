package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class FriendSearchRequest {
    private String keyword; // 搜索关键词（用户名/昵称）
}