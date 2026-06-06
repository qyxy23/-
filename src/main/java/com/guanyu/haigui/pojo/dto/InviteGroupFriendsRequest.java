package com.guanyu.haigui.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class InviteGroupFriendsRequest {
    @NotBlank(message = "群ID不能为空")
    private String groupId;

    /** 要邀请的好友 ID 列表 */
    @NotEmpty(message = "请至少选择一位好友")
    private List<Long> friendIds;
}
