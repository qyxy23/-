package com.guanyu.haigui.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        // 基于Spring Boot的默认配置构建ObjectMapper
        ObjectMapper objectMapper = builder.build();

        // 注册JavaTimeModule，支持LocalDateTime/LocaleDate等类型
        objectMapper.registerModule(new JavaTimeModule());

        // 关闭“日期转时间戳”（等价于application.properties中的配置）
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // （可选）设置全局时区
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        return objectMapper;
    }
}