package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表项VO（支持私聊/群聊）
 */
@Data
public class ChatSessionVO {
    /** 会话唯一标识：私聊=对方用户ID，群聊=群房间ID */
    private String sessionId;
    /** 会话类型：PRIVATE（私聊）/GROUP（群聊） */
    private String chatType;
    /** 会话名称：私聊=对方昵称，群聊=群名称 */
    private String chatName;
    /** 会话头像：私聊=对方头像，群聊=群头像 */
    private String chatAvatar;
    /** 未读消息数 */
    private Long unreadCount;
    /** 最后一条消息内容 */
    private String lastMessageContent;
    /** 最后一条消息时间 */
    private LocalDateTime lastMessageTime;
    /** 是否置顶（前端根据此字段排序） */
    private Boolean isSticky;
    /** 最后一条消息的发送者头像URL */
    private String lastSenderName;
}