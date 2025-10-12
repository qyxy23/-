package com.guanyu.haigui.controller;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.JoinChatRoomRequest;
import com.guanyu.haigui.pojo.vo.ChatRoomListVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.websocket.LobbyService;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;
import com.guanyu.haigui.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 聊天接口
 */
@AllArgsConstructor
@RestController
@RequestMapping("/chat")
@Tag(name = "聊天接口", description = "聊天相关接口")
public class ChatController {
    @Autowired
    private ChatService chatService;
    private final LobbyService lobbyService;
    private final SimpMessagingTemplate messagingTemplate; // 用于向客户端推送消息

    /**
     * 聊天
     *
     * @param roomId  聊天室 ID
     * @param message
     * @return
     */
    @Operation(summary = "聊天")
    @PostMapping("/{roomId}")
    public Result<String> doChat(@PathVariable Long roomId, @RequestParam String message) {
        return Result.success(chatService.chatWithAI(roomId, message));
    }

    /**
     * 获取聊天室列表
     *
     * @return
     */
    @Operation(summary = "获取聊天室列表内容")
    @GetMapping()
    public List<ChatRoomListVO> getChatRoomList() {
        // System.out.println("获取聊天室列表");
        // List<ChatRoom> chatRoomList = chatService.getChatRoomList();
        // System.out.println("chatRoomList = " + chatRoomList);
        // return chatRoomList;
        return chatService.getAIChatRoomListWithLastMessage(BaseContext.getCurrentId());
    }

    // 处理用户加入大厅的请求（前端发送到/app/chat.joinLobby）
    @Operation(summary = "处理用户加入大厅的请求")
    @MessageMapping("/chat/joinLobby")
    public void joinLobby(@Payload JoinChatRoomRequest request, Principal principal) {
        String userId = principal.getName(); // 从认证信息获取用户ID
        String lobbyId = request.getChatRoomId();

        // 加入大厅
        lobbyService.joinLobby(lobbyId, userId);

        // 订阅大厅的广播主题（客户端需自动订阅，或服务器通知前端订阅）
        // 前端需主动订阅：/topic/chat/{lobbyId}
        // 可选：通知大厅内其他成员“用户X加入”
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setRole(ChatMessageRole.valueOf("system"));
        chatMessage.setContent(userId + " 加入了大厅");
        messagingTemplate.convertAndSend("/topic/chat/" + lobbyId, chatMessage);
    }

    // 处理发送聊天消息的请求（前端发送到/app/chat.sendMessage）
    @Operation(summary = "处理发送聊天消息的请求")
    @MessageMapping("/chat/sendMessage")
    public void sendMessage(@Payload ChatMessage message, Principal principal) {
        String lobbyId = message.getToolCallId();
        String senderId = principal.getName();

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setRole(ChatMessageRole.valueOf("user"));
        chatMessage.setContent(message.getContent());

        // 广播消息到大厅的所有成员（/topic/chat/{lobbyId}）
        messagingTemplate.convertAndSend("/topic/chat/" + lobbyId, chatMessage);
    }
}
