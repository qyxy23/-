package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MessageChatType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class updateGroupAvatarVO {
    private String groupId;
    private String avatarUrl;
    private MessageChatType chatType;
}
