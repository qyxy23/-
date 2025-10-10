package com.guanyu.haigui.listerer;

import com.guanyu.haigui.websocket.LobbyService;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import lombok.AllArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@AllArgsConstructor
public class WebSocketDisconnectListener {
    private final LobbyService lobbyService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String userId = event.getUser().getName();
        // 遍历所有大厅，移除该用户
        lobbyService.getLobbies().forEach((lobbyId, members) -> {
            members.remove(userId);
            // 通知大厅成员"用户X离开"
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setRole(ChatMessageRole.valueOf("系统"));
            chatMessage.setContent(userId + " 离开了大厅");
            messagingTemplate.convertAndSend("/topic/chat/" + lobbyId, chatMessage);
        });
    }
}

