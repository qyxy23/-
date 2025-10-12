package com.guanyu.haigui.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        // 1. 配置服务器信息（可选）
        List<Server> servers = new ArrayList<>();
        servers.add(new Server().url("http://localhost:8080").description("本地开发环境"));

        // 2. 定义全局安全方案（Bearer Token）：补充描述引导用户
        SecurityScheme jwtSecurityScheme = new SecurityScheme()
                .name("Authorization") // 安全方案名称（必须与SecurityRequirement一致）
                .description("请输入Token（格式：`Bearer <你的Token内容>`，注意`Bearer`后加空格！）") // 🔥 关键：明确Token格式
                .type(SecurityScheme.Type.HTTP) // 认证类型：HTTP Header
                .scheme("bearer") // 固定为bearer（对应Bearer Token）
                .bearerFormat("JWT"); // Token格式（可选，标注JWT更清晰）

        // 3. 定义全局安全需求（所有接口默认需要Token）
        SecurityRequirement jwtSecurityRequirement = new SecurityRequirement()
                .addList("Authorization"); // 关联安全方案名称

        // 4. 构建OpenAPI实例
        return new OpenAPI()
                .servers(servers)
                .info(buildInfo()) // 文档基本信息
                .components(new Components()
                        .addSecuritySchemes("Authorization", jwtSecurityScheme)) // 注册安全方案
                .addSecurityItem(jwtSecurityRequirement); // 绑定全局安全需求
    }

    /**
     * 文档基本信息：补充认证说明
     */
    private Info buildInfo() {
        return new Info()
                .title("海龟汤 API 文档")
                .version("1.0")
                .description("接口需携带**Bearer Token**认证（格式：`Bearer <Token>`），未授权接口将返回403。") // 🔥 关键：文档说明
                .contact(new Contact()
                        .name("开发团队")
                        .url("https://github.com/your-repo")
                        .email("dev@guanyu.com"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("http://www.apache.org/licenses/LICENSE-2.0.html"));
    }
}