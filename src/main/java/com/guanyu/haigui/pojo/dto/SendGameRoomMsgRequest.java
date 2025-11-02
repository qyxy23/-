package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendGameRoomMsgRequest {
    @NotBlank(message = "群房间ID不能为空")
    private String roomId;          // 所属群聊ID

    @NotBlank(message = "消息内容不能为空")
    private String content;         // 消息内容

    @NotNull(message = "消息类型不能为空")
    private MessageType messageType;  // 消息类型（TEXT/IMAGE等）
}
