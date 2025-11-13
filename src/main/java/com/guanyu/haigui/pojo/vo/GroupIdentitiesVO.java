package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class GroupIdentitiesVO {
    private String groupId;
    private Long ownerId;
    private List<Long> admins;
}
