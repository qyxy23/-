package com.guanyu.haigui.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisStackConfig {
    private static final String REDIS_HOST = "localhost"; // Redis Stack地址
    private static final int REDIS_PORT = 6379; // Redis Stack端口
    private static final String REDIS_PASSWORD = ""; // 若有密码需填写

    // 获取Redisson客户端
    public static RedissonClient getClient() {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://" + REDIS_HOST + ":" + REDIS_PORT)
              .setPassword(REDIS_PASSWORD);
        return Redisson.create(config);
    }
}