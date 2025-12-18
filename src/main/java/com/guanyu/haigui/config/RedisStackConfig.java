package com.guanyu.haigui.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisStackConfig {

    // 从配置文件读取参数（推荐）
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:#{null}}") // 使用SpEL处理空密码
    private String redisPassword;

    /**
     * 创建 RedissonClient Bean
     * destroyMethod = "shutdown" 确保应用关闭时释放资源
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 构建连接地址
        String address = "redis://" + redisHost + ":" + redisPort;

        // 配置单节点模式
        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword);

        return Redisson.create(config);
    }
}