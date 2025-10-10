package com.guanyu.haigui.Hanldler;

import com.guanyu.haigui.manager.SessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    @Resource
    private SessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 客户端连接时，从Session中获取userId（需根据你的业务逻辑提取，比如URL参数、Token解析）
        String userId = getUserIdFromSession(session);
        sessionManager.addSession(userId, session);
        System.out.println("用户" + userId + "已连接，会话已添加");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 客户端断开时，清理会话
        String userId = getUserIdFromSession(session);
        sessionManager.removeSession(userId);
        System.out.println("用户" + userId + "已断开，会话已移除");
    }

    private String getUserIdFromSession(WebSocketSession session) {
        // 比如从URL参数中取：session.getUri().getQuery().split("=")[1]
        // 或从Token中解析：JWTUtil.getUserId(session.getHandshakeHeaders().get("Authorization").get(0))
        Map<String, Object> attributes = session.getAttributes();
        return (String) attributes.get("userId");
    }

    //TODO: 别人查看消息后要及时反馈
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        // 1. 从Session attributes中获取用户身份（握手时存入）
        Map<String, Object> attributes = session.getAttributes();
        Long userId = (Long) attributes.get("userId");
        String username = (String) attributes.get("username");

        // 2. 处理消息（如转发给其他用户）
        String payload = message.getPayload();
        System.out.println("收到用户" + username + "的消息：" + payload);

        // 3. 回复消息
        // TextMessage reply = new TextMessage("已收到你的消息：" + payload);
        // session.sendMessage(reply);
    }
}