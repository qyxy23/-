package com.guanyu.haigui.pojo.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class VoteCheckMessage implements Serializable {
    
    public enum MessageType {
        IMMEDIATE,  // 立即检查
        DELAYED,    // 延迟检查
        TIMEOUT     // 超时处理
    }
    
    private String voteSessionId;      // 投票会话ID
    private Long userId;                 // 用户ID（可选）
    private MessageType messageType;    // 消息类型
    private LocalDateTime triggerTime;  // 触发时间

    public static VoteCheckMessage createImmediateCheck(String voteSessionId) {
        return createMessage(voteSessionId, MessageType.IMMEDIATE);
    }
    
    public static VoteCheckMessage createDelayedCheck(String voteSessionId, int delayMinutes) {
        VoteCheckMessage msg = createMessage(voteSessionId, MessageType.DELAYED);
        msg.setTriggerTime(LocalDateTime.now().plusMinutes(delayMinutes));
        return msg;
    }
    
    public static VoteCheckMessage createTimeoutCheck(String voteSessionId) {
        return createMessage(voteSessionId, MessageType.TIMEOUT);
    }
    
    private static VoteCheckMessage createMessage(String voteSessionId, MessageType type) {
        VoteCheckMessage msg = new VoteCheckMessage();
        msg.setVoteSessionId(voteSessionId);
        msg.setMessageType(type);
        msg.setTriggerTime(LocalDateTime.now());
        return msg;
    }
}