package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MessageChatType;
import lombok.Data;

@Data
public class GroupMemberJoinVO {
    private String roomId;
    private Long userId;
    private String userName;
    private Long operatorId;
    private String operatorName;
    private MessageChatType chatType;
}
