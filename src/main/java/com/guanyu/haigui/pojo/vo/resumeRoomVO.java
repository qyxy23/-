package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.LobbyMemberStatus;
import lombok.Data;

@Data
public class resumeRoomVO {
    private String roomId;
    private Long userId;
    private LobbyMemberStatus status;
}
