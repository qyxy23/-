package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MessageChatType;
import com.guanyu.haigui.Enum.MessageStatus;
import com.guanyu.haigui.Enum.MessageType;
import com.guanyu.haigui.pojo.model.ChatGameMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群聊消息DTO（用于返回给前端，避免序列化代理对象）
 */
@Data
public class GameRoomMessageVO {
    @Schema(description = "消息唯一ID")
    private String messageId;
    @Schema(description = "所属群聊ID")
    private String roomId;
    @Schema(description = "发送者用户名")
    private String senderUsername;
    @Schema(description = "发送者头像")
    private String senderAvatar;
    @Schema(description = "消息内容")
    private String content;
    @Schema(description = "消息类型")
    private MessageType messageType;
    @Schema(description = "消息状态")
    private MessageStatus status;
    @Schema(description = "消息发送时间")
    private LocalDateTime createTime;
    @Schema(description = "发送者ID")
    private String senderId;
    @Schema(description = "消息类型")
    private MessageChatType chatType;

    public static GameRoomMessageVO from(ChatGameMessage message) {
        GameRoomMessageVO vo = new GameRoomMessageVO();
        vo.setMessageId(message.getMessageId());
        vo.setRoomId(message.getChatGame().getRoomId()); // 直接取群聊ID（无需代理）
        vo.setContent(message.getContent());
        vo.setMessageType(message.getMessageType());
        vo.setStatus(message.getStatus());
        vo.setCreateTime(message.getCreateTime());
        if (message.getSender() != null) {
            vo.setSenderId(message.getSender().getUserId().toString());
            vo.setSenderUsername(message.getSender().getUsername());
            vo.setSenderAvatar(message.getSender().getAvatar());
        }
        // 默认设置chatType为LOBBY_MESSAGE，但允许外部覆盖
        vo.setChatType(MessageChatType.LOBBY_MESSAGE);
        return vo;
    }
}