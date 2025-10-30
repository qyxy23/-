package com.guanyu.haigui.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FriendSearchListVO {
    // 用户ID
    private Long userId;
    // 用户名
    @Schema(description = "用户名")
    private String username;
    // 头像
    @Schema(description = "头像")
    private String avatar;
    // 状态
    // private boolean enabled;
    // 未读消息数
    private Long unreadCount;
    // 最后一条消息
    private String lastMessageContent;
    // 发送时间
    @Schema(description = "发送时间")
    private LocalDateTime lastMessageTime;
}
