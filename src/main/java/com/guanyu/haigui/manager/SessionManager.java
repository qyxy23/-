package com.guanyu.haigui.manager;

import com.google.gson.Gson;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Set;

/**
 * @author Guanyu
 * 聊天会话管理器
 */
@Component
public class SessionManager {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 存储用户ID→WebSocket Session的映射
    private static final String SESSION_KEY = "chat:sessions:";
    // 存储在线用户列表
    private static final String ONLINE_USERS_KEY = "chat:online_users";

    /**
     * 添加用户会话
     *
     * @param userId
     * @param session
     */
    public void addSession(String userId, WebSocketSession session) {
        redisTemplate.opsForHash().put(SESSION_KEY + userId, "session", session);
        redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId);
    }

    /**
     * 移除用户会话
     *
     * @param userId
     */
    public void removeSession(String userId) {
        redisTemplate.delete(SESSION_KEY + userId);
        redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId);
    }

    /**
     * 广播消息给所有在线用户
     *
     * @param message
     */
    public void broadcastMessage(ChatMessage message) {
        // 遍历所有在线用户的Session，发送消息
        // Set<String> onlineUsers = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
        Set<String> onlineUsers = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
        for (String userId : onlineUsers) {
            WebSocketSession session = (WebSocketSession) redisTemplate.opsForHash().get(SESSION_KEY + userId, "session");
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message.toJson()));
            }
        }
    }

    /**
     * 广播在线用户列表给所有用户
     */
    public void broadcastOnlineUsers() {
        // 广播在线用户列表给所有用户
        Set<String> onlineUsers = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
        List<User> users = userService.getUsersByIds(new ArrayList<>(onlineUsers));
        String onlineUserListJson = new Gson().toJson(users);
        // 发送给所有在线用户...
    }
}