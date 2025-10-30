package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.PrivateMessageDTO;
import com.guanyu.haigui.pojo.vo.PrivateMessageVO;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.MessageService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@AllArgsConstructor
@Controller
public class ChatWithFriendController {
    private final MessageService messageService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserInfoRepository userRepository;



    // 处理客户端发送的消息
    @MessageMapping("/app/chat.sendMessage")
    public PrivateMessageVO sendMessage(PrivateMessageDTO message) {
        return messageService.sendMessage(message);
    }

    // 客户端接收的消息格式
    @Data
    public static class ChatMessage {
        private Long senderId;
        private Long receiverId;
        private String content;
        private String messageType; // 对应MessageType枚举
    }
}