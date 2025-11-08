package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.JoinGroupStatus;
import com.guanyu.haigui.Enum.MessageChatType;
import lombok.AllArgsConstructor;
import lombok.Data;
@AllArgsConstructor
@Data
public class JoinGroupRoomVO {
    private Long userId;
    private String groupRoomId;
    private String groupName;
    private String groupAvatar;
    private JoinGroupStatus joinGroupStatus;
    private MessageChatType chatType;
}
