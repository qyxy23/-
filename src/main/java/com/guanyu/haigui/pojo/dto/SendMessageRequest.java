package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendMessageRequest {
    // 消息类型：GROUP（群聊）/PRIVATE（私聊）→ 前端用字符串传递，服务端转枚举
    @NotBlank(message = "消息类型不能为空")
    private String chatType;

    // 群聊时必填：群房间ID
    @NotBlank(message = "群房间ID不能为空", groups = GroupMessage.class)
    private String roomId;

    // 私聊时必填：接收者用户ID
    @NotNull(message = "接收者ID不能为空", groups = PrivateMessage.class)
    private Long receiverId;

    // 消息内容（非空）
    @NotBlank(message = "消息内容不能为空")
    private String content;

    // 消息介质类型（文本/图片等）
    @NotNull(message = "消息类型不能为空")
    private MessageType messageType;
}

// 分组校验：用于区分群聊/私聊的校验规则
interface GroupMessage {}
interface PrivateMessage {}