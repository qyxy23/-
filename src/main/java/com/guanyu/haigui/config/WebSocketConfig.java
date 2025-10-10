package com.guanyu.haigui.config;

import com.guanyu.haigui.Hanldler.ChatWebSocketHandler;
import com.guanyu.haigui.interceptor.JwtWebSocketInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketConfigurer,WebSocketMessageBrokerConfigurer {

    private final JwtWebSocketInterceptor jwtInterceptor;
    private final ChatWebSocketHandler chatHandler;

    public WebSocketConfig(JwtWebSocketInterceptor jwtInterceptor, ChatWebSocketHandler chatHandler) {
        this.jwtInterceptor = jwtInterceptor;
        this.chatHandler = chatHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/ws")
                .addInterceptors(jwtInterceptor); // 注册JWT拦截器
    }

    // 注册WebSocket端点（前端连接入口）
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws") // 前端连接的URL
                .setAllowedOriginPatterns("*") // 允许跨域
                .withSockJS(); // 支持SockJS回退
    }

    // 配置消息代理（用于广播消息）
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue"); // 客户端订阅的前缀（/topic用于广播，/queue用于点对点）
        registry.setApplicationDestinationPrefixes("/app"); // 客户端发送消息的前缀（如/app/chat.join）
    }
}