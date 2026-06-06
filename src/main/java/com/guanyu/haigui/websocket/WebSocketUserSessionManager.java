package com.guanyu.haigui.websocket;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 维护 userId ↔ STOMP sessionId 映射，用于 convertAndSendToUser 注册表缺失时的兜底推送。
 */
@Component
public class WebSocketUserSessionManager {

    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<String>> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sessionOwners = new ConcurrentHashMap<>();

    public void register(Long userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        userSessions.computeIfAbsent(userId, ignored -> new CopyOnWriteArraySet<>()).add(sessionId);
        sessionOwners.put(sessionId, userId);
    }

    public void unregister(String sessionId) {
        if (sessionId == null) {
            return;
        }
        Long userId = sessionOwners.remove(sessionId);
        if (userId == null) {
            return;
        }
        CopyOnWriteArraySet<String> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
    }

    public Set<String> getSessionIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        CopyOnWriteArraySet<String> sessions = userSessions.get(userId);
        return sessions == null ? Set.of() : Collections.unmodifiableSet(sessions);
    }

    public int sessionCount(Long userId) {
        return getSessionIds(userId).size();
    }
}
