package com.guanyu.haigui.config;

import com.volcengine.ark.runtime.service.ArkService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "ai")
@Data
public class AIConfig {

    @Value("${ai.api-key:}")
    private String apiKey;

    /** 文生图推理接入点（如 ep-xxx），需在火山方舟控制台开通 */
    private String imageEndpoint = "";

    /** 生成尺寸：3:1 宽图；Seedream 要求总像素 ≥ 3686400（如 3840×1280） */
    private String imageSize = "3840x1280";

    @Bean
    public ArkService getArkService() {
        // 检查apiKey是否为空，如果为空则返回null而不是抛出异常
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("AI API Key is not configured, AI service will be disabled");
            return null;
        }

        String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        return ArkService.builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }
}