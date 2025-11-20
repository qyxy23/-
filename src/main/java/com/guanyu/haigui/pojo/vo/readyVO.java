package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.LobbyMemberStatus;
import lombok.Data;

@Data
public class readyVO {
    private String roomId;
    private Long memberId;
    private LobbyMemberStatus status;
}
