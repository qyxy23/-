package com.guanyu.haigui;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

//RedisHash注解会将实体类映射为 Redis 的 Hash 类型，但是当前并不需要 Redis 存储，因此先排除 Redis 配置
@SpringBootApplication(exclude = {RedisRepositoriesAutoConfiguration.class})
@EnableMethodSecurity()
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@MapperScan(basePackages = "com.guanyu.haigui.mapper")
public class HaiGuiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HaiGuiApplication.class, args);
    }

}
