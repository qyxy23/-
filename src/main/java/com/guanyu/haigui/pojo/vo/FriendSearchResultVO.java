package com.guanyu.haigui.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 好友搜索结果DTO
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FriendSearchResultVO {
    // 用户ID
    private Long userId;
    // 用户名
    @Schema(description = "用户名")
    private String username;
    // 头像
    @Schema(description = "头像")
    private String avatar;
    // 状态
    private boolean enabled;
}