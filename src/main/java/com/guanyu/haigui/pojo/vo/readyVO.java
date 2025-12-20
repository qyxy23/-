package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MessageChatType;
import lombok.Data;

@Data
public class readyVO {
    private String roomId;
    private Long userId;
    private MessageChatType chatType;
}
