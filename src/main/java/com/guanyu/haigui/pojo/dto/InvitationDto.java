package com.guanyu.haigui.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class InvitationDto {
    @NotBlank(message = "房间ID不能为空")
    private String roomId;       // 目标房间ID（UUID）

    @NotBlank(message = "被邀请者ID不能为空")
    private List<Long> inviteeId;      // 被邀请者用户ID
}
