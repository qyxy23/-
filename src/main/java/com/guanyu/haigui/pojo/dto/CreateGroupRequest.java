package com.guanyu.haigui.pojo.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
@Data
public class CreateGroupRequest {
    /** 要添加的好友ID列表（至少1个） */
    @NotEmpty(message = "至少需要添加一位好友")
    private List<Long> friendIds;
}
