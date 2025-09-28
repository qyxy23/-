package com.guanyu.haigui.Hanldler;

import com.guanyu.haigui.manager.SessionManager;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    @Autowired
    private SessionManager sessionManager;
    @Autowired
    private MessageService messageService;

    // 用户连接时触发
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = getUserIdFromSession(session); // 从Session中获取用户ID（比如JWT令牌解析）
        sessionManager.addSession(userId, session); // 加入在线用户列表
        sessionManager.broadcastOnlineUsers(); // 广播在线用户列表给所有用户
    }

    // 接收用户消息时触发
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = getUserIdFromSession(session);
        String content = message.getPayload();
        // 保存消息到数据库（可选）
        ChatMessage chatMessage = messageService.saveMessage(userId, content);
        // 广播消息给所有在线用户
        sessionManager.broadcastMessage(chatMessage);
    }

    // 用户断开连接时触发
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = getUserIdFromSession(session);
        sessionManager.removeSession(userId); // 移除在线用户
        sessionManager.broadcastOnlineUsers(); // 更新在线列表
    }
}