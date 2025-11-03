package com.guanyu.haigui.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
@Data
public class CreatorInfoVO {
    private Long userId;
    // 用户名
    @Schema(description = "用户名")
    private String username;
    // 头像
    @Schema(description = "头像")
    private String avatar;
}
