package com.guanyu.haigui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.cors")
public class AppCorsProperties {

    /** 允许的跨域来源，生产环境应配置为具体域名 */
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));
}
