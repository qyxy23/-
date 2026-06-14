package com.guanyu.haigui.pojo.vo;

import lombok.Data;

@Data
public class LobbyShareTokenVO {
    private String roomId;
    private String shareToken;
    /** 过期时间戳（毫秒） */
    private Long expiresAt;
}
