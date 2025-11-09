package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.MessageChatType;
import com.guanyu.haigui.Enum.RequestStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupJoinNotification {
    private Long requestId; // 申请ID
    private String applicantName; // 申请人用户名
    private String description; // 加群描述
    private String groupName;
    private RequestStatus status;
    private LocalDateTime applyTime;
    private MessageChatType chatType;
}