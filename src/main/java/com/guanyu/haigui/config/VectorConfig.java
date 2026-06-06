package com.guanyu.haigui.config;

import com.guanyu.haigui.utils.BgeVectorClientUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量化服务配置类
 * 确保向量化相关的Bean正确配置和注入
 */
@Configuration
@EnableConfigurationProperties(VectorSearchProperties.class)
public class VectorConfig {

    /**
     * 配置BGE向量化客户端
     * 确保静态工具类能正确获取配置
     */
    @Bean
    public BgeVectorClientUtil bgeVectorClientUtil() {
        return new BgeVectorClientUtil();
    }
}