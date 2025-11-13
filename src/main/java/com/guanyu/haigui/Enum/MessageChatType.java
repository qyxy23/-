package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum MessageChatType {
    GROUP_MESSAGE("群消息"),
    PRIVATE_MESSAGE("私聊消息"),
    GROUP_JOIN_REQUESTS("加入群聊申请"),
    FRIEND_JOIN_REQUESTS("好友申请"),
    GROUP_AGREE_REQUESTS("同意加入群聊申请"),
    GROUP_REFUSE_REQUESTS("拒绝加入群聊申请"),
    GROUP_RETRACT_REQUESTS("撤回加入群聊申请"),
    FRIEND_RETRACT_REQUESTS("撤回好友申请"),
    GROUP_LEAVE("离开群聊"),
    GROUP_UPDATE_NAME("修改群名称"),
    LOBBY_MESSAGE("大厅消息"),
    // VOICE("语音"),
    VIDEO("视频"), GROUP_UPDATE_AVATAR("修改群头像");

    private final String description;

    MessageChatType(String description) {
        this.description = description;
    }

}
