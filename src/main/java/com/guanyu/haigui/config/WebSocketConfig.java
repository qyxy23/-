package com.guanyu.haigui.config;

import com.guanyu.haigui.interceptor.JwtWebSocketInterceptor;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
@AllArgsConstructor
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
// public class WebSocketConfig implements WebSocketConfigurer,WebSocketMessageBrokerConfigurer {
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtWebSocketInterceptor jwtInterceptor;

    // 注册WebSocket端点（前端连接入口）

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws") // 前端连接的URL
                .setAllowedOriginPatterns("*") // 允许跨域
                .addInterceptors(jwtInterceptor)// 注册JWT拦截器
                .withSockJS(); // 支持SockJS回退
    }

    // 配置消息代理（用于广播消息）
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue"); // 客户端订阅的前缀（/topic用于广播，/queue用于点对点）
        registry.setApplicationDestinationPrefixes("/app"); // 客户端发送消息的前缀（如/app/chat.join）
    }

    /**
     * 配置Redis消息监听：用于集群间的消息广播（分布式）
     */
    /*@Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new PatternTopic("/topic/public"));
        return container;
    }*/


    // private final ChatWebSocketHandler chatHandler;


    // @Override
    // public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    //     registry.addHandler(chatHandler, "/ws")
    //             .addInterceptors(jwtInterceptor); // 注册JWT拦截器
    // }

}