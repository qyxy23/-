package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.model.ChatRoomMember;
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

    private Integer requiredMembers;

    private Integer currentMembers;

    private RoomStatus status;

    private LocalDateTime createTime;


    // 成员列表（可选，用于查询）
    private Set<ChatRoomMember> members = new HashSet<>();
}
