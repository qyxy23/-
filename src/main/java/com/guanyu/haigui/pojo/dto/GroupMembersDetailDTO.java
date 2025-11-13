package com.guanyu.haigui.pojo.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class GroupMembersDetailDTO {
    /** 群ID（必填） */
    @NotBlank(message = "群ID不能为空")
    private String groupId;

    /** 需要查询的发送者ID列表（必填） */
    @NotEmpty(message = "发送者ID列表不能为空")
    private List<Long> senderIds;

    /** 当前登录用户ID（从Token中解析，无需前端传递） */
    private Long currentUserId;
}