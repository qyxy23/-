package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class CreateRoomRequest {
    private String roomName;
    private String soupId;
    private Integer requiredMembers;
}