package com.guanyu.haigui.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
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
}