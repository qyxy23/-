package com.guanyu.haigui.manager;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class SessionManager {
    // Redis中存储会话元数据的键前缀（如：chat:sessions:123 → 存储sessionId、connectedAt）
    private static final String SESSION_META_KEY = "chat:sessions:";
    // Redis中存储在线用户的集合（如：chat:online_users → ["123", "456"]）
    private static final String ONLINE_USERS_KEY = "chat:online_users";
    // 本地内存存储：userId → WebSocketSession（单机场景快速获取会话）
    private final ConcurrentHashMap<Long, WebSocketSession> localSessionMap = new ConcurrentHashMap<>();

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 添加用户会话（连接建立时调用）
     */
    public void addSession(Long userId, WebSocketSession session) {
        // 1. 存储会话元数据到Redis（所有值必须是字符串！）
        redisTemplate.opsForHash().put(SESSION_META_KEY + userId, "sessionId", session.getId());
        redisTemplate.opsForHash().put(SESSION_META_KEY + userId, "connectedAt", String.valueOf(System.currentTimeMillis()));

        // 2. 存储到本地内存（快速获取会话）
        localSessionMap.put(userId, session);

        // 3. 标记用户为在线
        redisTemplate.opsForSet().add(ONLINE_USERS_KEY, String.valueOf(userId));
    }

    /**
     * 移除用户会话（连接关闭时调用）
     */
    public void removeSession(Long userId) {
        // 1. 删除Redis中的会话元数据
        redisTemplate.delete(SESSION_META_KEY + userId);

        // 2. 从本地内存移除
        localSessionMap.remove(userId);

        // 3. 标记用户为离线
        redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, String.valueOf(userId));
    }

    /**
     * 获取用户的WebSocketSession（从本地内存取，快速高效）
     */
    public WebSocketSession getSession(Long userId) {
        return localSessionMap.get(userId);
    }

    /**
     * 获取所有在线用户的ID（字符串集合）
     */
    public Set<String> getOnlineUserIdsAsString() {
        Set<String> rawUsers = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
        return rawUsers == null ? Collections.emptySet() :
                rawUsers.stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.toSet());
    }
}