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
    private String roomId;              // 房间ID
    private Long userId;                 // 用户ID（可选）
    private MessageType messageType;    // 消息类型
    private LocalDateTime triggerTime;  // 触发时间
    private int requiredAgreeRatio;     // 所需同意比例（默认60%）
    
    public static VoteCheckMessage createImmediateCheck(String voteSessionId, String roomId) {
        return createMessage(voteSessionId, roomId, MessageType.IMMEDIATE);
    }
    
    public static VoteCheckMessage createDelayedCheck(String voteSessionId, String roomId, int delayMinutes) {
        VoteCheckMessage msg = createMessage(voteSessionId, roomId, MessageType.DELAYED);
        msg.setTriggerTime(LocalDateTime.now().plusMinutes(delayMinutes));
        return msg;
    }
    
    public static VoteCheckMessage createTimeoutCheck(String voteSessionId, String roomId) {
        return createMessage(voteSessionId, roomId, MessageType.TIMEOUT);
    }
    
    private static VoteCheckMessage createMessage(String voteSessionId, String roomId, MessageType type) {
        VoteCheckMessage msg = new VoteCheckMessage();
        msg.setVoteSessionId(voteSessionId);
        msg.setRoomId(roomId);
        msg.setMessageType(type);
        msg.setTriggerTime(LocalDateTime.now());
        msg.setRequiredAgreeRatio(60); // 默认60%
        return msg;
    }
}