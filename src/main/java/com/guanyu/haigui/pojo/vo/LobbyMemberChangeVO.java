package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guanyu.haigui.Enum.MessageChatType;
import lombok.Data;

/**
 * 房间成员变动 WebSocket 广播（事务提交后推送，附带最新成员快照）
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LobbyMemberChangeVO {
    private MessageChatType chatType;
    private searchAllLobbyMemberVO memberSnapshot;
    private Long userId;
    private String userName;
    private String userAvatar;
    private String roomId;
    /** 扩展事件状态，如 BECOME_OWNER */
    private String eventStatus;
}
