package com.guanyu.haigui.utils;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CustomSessionRegistry implements ApplicationListener<AbstractSubProtocolEvent> {

    // 存储会话ID → WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void onApplicationEvent(@NotNull AbstractSubProtocolEvent event) {
        if (event instanceof SessionConnectEvent) {
            // 连接成功时，记录会话
            WebSocketSession session = event.getMessage().getHeaders().get("simpSession", WebSocketSession.class);
            // String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
            if (session != null) {
                sessions.put(session.getId(), session);
            }
        } else if (event instanceof SessionDisconnectEvent) {
            // 断开时，移除会话
            sessions.remove(((SessionDisconnectEvent) event).getSessionId());
        }
    }

    // 提供获取会话的方法
    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }
}
