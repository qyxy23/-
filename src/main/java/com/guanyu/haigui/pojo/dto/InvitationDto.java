package com.guanyu.haigui.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class InvitationDto {
    private String roomId;
    private List<Long> inviteeIds;
}
