package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MessageChatType;
import lombok.Data;

@Data
public class updateGroupNameVO {
    private String groupId;
    private String groupName;
    private MessageChatType chatType;
}
