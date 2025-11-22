package com.guanyu.haigui.config;

import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量化服务配置类
 * 确保向量化相关的Bean正确配置和注入
 */
@Configuration
@EnableConfigurationProperties
public class VectorConfig {

    /**
     * 配置BGE向量化客户端
     * 确保静态工具类能正确获取配置
     */
    @Bean
    public BgeVectorClientUtil bgeVectorClientUtil() {
        return new BgeVectorClientUtil();
    }

    /**
     * 配置Redis Stack客户端
     */
    @Bean
    public RedisStackClient redisStackClient() {
        return new RedisStackClient();
    }
}