package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.FriendStatus;
import com.guanyu.haigui.Enum.MessageChatType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FriendRetractNotificationVO {
    private Long applicantId;
    private FriendStatus status;
    private MessageChatType chatType;
}
