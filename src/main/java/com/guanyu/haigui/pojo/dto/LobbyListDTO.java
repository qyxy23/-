package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.RoomStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LobbyListDTO {
    private Boolean excludeInvited = true; // 默认排除邀请房间

    // 聊天室名称
    private String roomName;

    private RoomStatus status;

    private Integer requiredMembers;

    private Integer currentMembers;

    private LocalDateTime createTime;
}
