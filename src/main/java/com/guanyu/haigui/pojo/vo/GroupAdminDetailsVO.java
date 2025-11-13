package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class GroupAdminDetailsVO {
    private String groupId;
    private List<AdminDetailsVO> admins;
}
