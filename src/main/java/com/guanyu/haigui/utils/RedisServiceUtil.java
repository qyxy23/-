package com.guanyu.haigui.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
public class RedisServiceUtil {

    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private JwtTokenUtil jwtUtil;
    private static final String USER_ONLINE_KEY_PREFIX = "user:online:"; // Redis 在线状态键前缀
    private static final String ROOM_ONLINE_KEY_PREFIX = "room:online:"; // Redis 在线状态键前缀
    // 存储用户ID→WebSocket Session的映射
    private static final String SESSION_KEY = "chat:sessions:";
    // 存储在线用户列表
    private static final String ONLINE_USERS_KEY = "chat:online_users";


    public void updateSession(Long userId, WebSocketSession session) {
        // 存储会话ID而不是整个session对象
        redisTemplate.opsForHash().put(SESSION_KEY + userId, "sessionId", session.getId());
        // 存储其他需要的会话信息
        redisTemplate.opsForHash().put(SESSION_KEY + userId, "connectedAt", System.currentTimeMillis());

        redisTemplate.opsForSet().add(ONLINE_USERS_KEY , String.valueOf(userId));
    }

    public void deleteSession(Long userId) {
        redisTemplate.delete(SESSION_KEY + userId);
        redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId);
    }

    public void updateOnlineStatus(Long id, String token) {
        Date tokenExpiration = jwtUtil.getExpirationDateFromToken(token); // 需要 JwtUtil 支持
        long expireMillis = tokenExpiration.getTime() - System.currentTimeMillis();
        // 设置 Redis 键：值为 "1"（在线），过期时间与 Token 一致
        if (expireMillis > 0) {
            redisTemplate.opsForValue().set(USER_ONLINE_KEY_PREFIX + id, "1", expireMillis, TimeUnit.MILLISECONDS);
        } else {
            // 若 Token 无过期时间，设置默认 30 分钟过期
            redisTemplate.opsForValue().set(USER_ONLINE_KEY_PREFIX + id, "1", 30, TimeUnit.MINUTES);
        }
    }

    public void deleteOnlineStatus(Long id) {
        redisTemplate.delete(USER_ONLINE_KEY_PREFIX + id);
    }

    public boolean selectOnlineRooms(String roomId) {
        String value =  redisTemplate.opsForValue().get(ROOM_ONLINE_KEY_PREFIX + roomId);
        return "1".equals(value);
    }

    public void updateOnlineRooms(String roomId) {

        redisTemplate.opsForValue().set(ROOM_ONLINE_KEY_PREFIX + roomId, "1", 300000000, TimeUnit.MINUTES);
    }

    public void deleteOnlineRooms(Long roomId) {
        redisTemplate.delete(ROOM_ONLINE_KEY_PREFIX + roomId);
    }


    @PostConstruct
    public void initRedisTemplate() {
        // 设置Key和Value的序列化器为字符串
        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setValueSerializer(RedisSerializer.string());
        // 若有Hash结构需求，同步设置Hash的序列化器
        redisTemplate.setHashKeySerializer(RedisSerializer.string());
        redisTemplate.setHashValueSerializer(RedisSerializer.string());
    }

    public void updateOnlineRoomsAndNumbers(String roomId, int num) {
        redisTemplate.opsForValue().set(ROOM_ONLINE_KEY_PREFIX + roomId + ":survive", "1", 300000000, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(ROOM_ONLINE_KEY_PREFIX + roomId + ":num", String.valueOf(num), 300000000, TimeUnit.MINUTES);
    }
}