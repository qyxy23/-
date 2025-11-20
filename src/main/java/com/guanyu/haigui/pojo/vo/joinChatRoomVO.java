package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.LobbyMemberStatus;
import lombok.Data;

@Data
public class joinChatRoomVO {
    private Long userId;
    private String userName;
    private String userAvatar;
    private LobbyMemberStatus status;
}
