package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.model.UserInfo;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
public class LobbyListVO {
    private String roomId;

    // 聊天室名称
    private String roomName;

    private UserInfo creator; // 关联用户表

    private Long requiredMembers;

    private Long currentMembers;

    private RoomStatus status;

    private LocalDateTime createTime;


    private Set<MemberSimpleVO> members = new HashSet<>();
}
