package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MessageChatType;
import com.guanyu.haigui.Enum.RequestStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupApplyRetractNotificationVO {
    private Long requestId; // 申请ID
    private RequestStatus status;
    private LocalDateTime retractTime;
    private MessageChatType chatType;
}
