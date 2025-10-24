package com.guanyu.haigui.controller;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.CreateRoomRequest;
import com.guanyu.haigui.pojo.dto.JoinChatRoomRequest;
import com.guanyu.haigui.service.ServicesImpl.ChatRoomService;
import com.guanyu.haigui.websocket.LobbyService;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@AllArgsConstructor
@Controller
public class ChatWithFriendsController {
    private final LobbyService lobbyService;
    private final SimpMessagingTemplate messagingTemplate; // 用于向客户端推送消息
    private final ChatRoomService chatRoomService;

    // 处理用户加入大厅的请求（前端发送到/app/chat.joinLobby）
    @Operation(summary = "处理用户加入大厅的请求")
    @MessageMapping("/ws/joinRoom")
    public void joinRoom(@Payload JoinChatRoomRequest request) {
        String userId = BaseContext.getCurrentId().toString(); // 从认证信息获取用户ID
        String roomId = request.getChatRoomId();

        log.info("用户{}加入大厅{}", userId, roomId);
        // 加入大厅
        lobbyService.joinLobby(roomId, userId);

        // 订阅大厅的广播主题（客户端需自动订阅，或服务器通知前端订阅）
        // 前端需主动订阅：/topic/chat/{lobbyId}
        // 可选：通知大厅内其他成员“用户X加入”
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setRole(ChatMessageRole.valueOf("system"));
        chatMessage.setContent(userId + " 加入了大厅");
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, chatMessage);
    }

    // 处理发送聊天消息的请求（前端发送到/app/chat.sendMessage）
    @Operation(summary = "处理发送聊天消息的请求")
    @MessageMapping("/ws/sendMessage")
    public void sendMessage(@Payload ChatMessage message ) {
        String lobbyId = message.getToolCallId();
        String senderId = BaseContext.getCurrentId().toString();
        log.info("用户{}发送消息到大厅{}", senderId, lobbyId);
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setRole(ChatMessageRole.valueOf("user"));
        chatMessage.setContent(message.getContent());

        // 广播消息到大厅的所有成员（/topic/chat/{lobbyId}）
        messagingTemplate.convertAndSend("/topic/chat/" + lobbyId, chatMessage);
    }

    @Operation(summary = "创建大厅")
    @MessageMapping("/createLobby")
    public Map<String, String> createLobby(@Payload CreateRoomRequest request, @Header("simpSessionId") String sessionId) {
        String roomName = request.getRoomName();
        Integer requiredMembers = request.getRequiredMembers();
        String userId = BaseContext.getCurrentId().toString();
        log.info("用户{}创建{}人大厅{}，会话ID：{}", userId, requiredMembers, roomName, sessionId);
        // 创建大厅
        String roomId = chatRoomService.createChatRoom(roomName, requiredMembers, BaseContext.getCurrentId());
        
        // 返回房间ID和会话ID
        Map<String, String> result = new HashMap<>();
        result.put("roomId", roomId);
        result.put("sessionId", sessionId);
        return result;
    }

}
