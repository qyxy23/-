package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class SearchLobbiesMessage {
    private LobbyListDTO dto; // 查询条件（如名称、时间范围等）
    private int page;        // 页码（前端习惯从1开始）
}