package com.guanyu.haigui.manager;

import com.google.gson.Gson;
import com.guanyu.haigui.mapper.UserDetailsMapper;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Guanyu
 * 聊天会话管理器
 */
@Component
public class SessionManager {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private UserDetailsMapper userDetailsMapper;

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
    public void broadcastMessage(ChatMessage message) throws IOException {
        // 获取在线用户ID集合并转换为Set<String>
        Set<String> onlineUsers = getOnlineUsersAsStringSet();
        for (String userId : onlineUsers) {
            WebSocketSession session = (WebSocketSession) redisTemplate.opsForHash().get(SESSION_KEY + userId, "session");
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(message.toString()));
            }
        }
    }

    /**
     * 广播在线用户列表给所有用户
     */
    public void broadcastOnlineUsers() {
        // 获取在线用户ID集合并转换为Set<String>
        Set<String> onlineUsers = getOnlineUsersAsStringSet();
        List<String> userIds = new ArrayList<>(onlineUsers);
        List<UserInfo> users = userDetailsMapper.getUsersByIds(userIds);
        String onlineUserListJson = new Gson().toJson(users);
        // 发送给所有在线用户...（此处添加实际广播逻辑）
    }

    /**
     * 获取在线用户ID集合（转换为Set<String>）
     */
    private Set<String> getOnlineUsersAsStringSet() {
        Set<Object> rawUsers = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
        if (rawUsers == null) {
            return Collections.emptySet();
        }
        return rawUsers.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}