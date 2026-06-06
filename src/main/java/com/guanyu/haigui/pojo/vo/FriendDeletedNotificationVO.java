package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MessageChatType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FriendDeletedNotificationVO {
    private MessageChatType chatType;
    /** 被删除关系中的对方用户 ID */
    private Long peerUserId;
}
