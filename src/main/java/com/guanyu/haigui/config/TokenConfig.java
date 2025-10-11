package com.guanyu.haigui.config;

import com.guanyu.haigui.interceptor.TokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * Spring MVC配置类：注册TokenInterceptor
 */
@Configuration
public class TokenConfig implements WebMvcConfigurer {

    /**
     * 注入TokenInterceptor（需确保TokenInterceptor是@Component注解的Spring Bean）
     */
    @Resource
    private TokenInterceptor tokenInterceptor;

    /**
     * 注册拦截器：指定拦截路径、排除路径、拦截器顺序
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor) // 注册TokenInterceptor
                // 拦截所有HTTP请求（可根据需求调整，如"/api/**"）
                .addPathPatterns("/**")
                // 排除无需校验Token的接口（关键：避免拦截登录、静态资源等）
                .excludePathPatterns(
                        "/user/**", // 登录接口
                        // "/user/register", // 注册接口
                        // "/user/logout", // 登出接口
                        "/swagger-ui/**", // Swagger UI界面
                        "/v3/api-docs/**", // Swagger API文档
                        "/v3/api-docs", // Swagger API文档根路径
                        "/webjars/**", // Swagger静态资源
                        "/doc.html", // Knife4j文档页面
                        "/favicon.ico", // 网站图标
                        "/error" // 错误页面
                )
                // 设置拦截器顺序（数字越小，优先级越高；可选，默认顺序由添加顺序决定）
                .order(1);
    }
}