package com.guanyu.haigui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Bean
    public CorsFilter corsFilter() {
        // 1. 配置 CORS 规则
        CorsConfiguration config = new CorsConfiguration();
        // 允许前端源（开发阶段用 *，生产环境建议指定具体源如 http://localhost:5173）
        config.addAllowedOrigin("*");
        // 允许的 HTTP 方法（必须包含 OPTIONS，预检请求需要）
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("OPTIONS");
        // 允许的请求头（如 Content-Type、Authorization 等）
        config.addAllowedHeader("*");
        // 允许前端读取的响应头（可选）
        config.addExposedHeader("*");
        // 是否允许发送 Cookie（若需要，设为 true，此时 allowedOrigin 不能为 *）
        config.setAllowCredentials(false);

        // 2. 映射所有接口路径
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // 所有接口都支持 CORS

        // 3. 返回 CorsFilter
        return new CorsFilter(source);
    }

    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                //允许发送cookie
                .allowCredentials(true)
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}