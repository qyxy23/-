package com.guanyu.haigui.config;

import com.guanyu.haigui.interceptor.JwtWebSocketInterceptor;
import com.guanyu.haigui.interceptor.WebSocketSecurityInterceptor;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppCorsProperties appCorsProperties;

    @Resource
    private JwtWebSocketInterceptor jwtWebSocketInterceptor;
    @Resource
    @Lazy
    private WebSocketSecurityInterceptor webSocketSecurityInterceptor;

    /**
     * 配置客户端入站通道，用于处理从客户端接收的消息
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 注册自定义WebSocket安全拦截器，确保在Spring Security拦截器之前执行
        registration.interceptors(webSocketSecurityInterceptor);
        // 注意：Spring Security的安全拦截器将由@EnableWebSocketSecurity自动配置
    }

    @Bean
    public ConcurrentHashMap<String, CustomUserDetails> sessionUserMap() {
        return new ConcurrentHashMap<>();
    }

    /**
     * 注册WebSocket端点（前端连接入口）
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(appCorsProperties.getAllowedOriginPatterns().toArray(new String[0]))
                .addInterceptors(jwtWebSocketInterceptor);
        // 移除.withSockJS()以支持原生WebSocket连接（兼容移动端和小程序）
    }

    /**
     * 配置WebSocket处理链
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // 设置最大消息大小等配置
        registration.setMessageSizeLimit(8192);
        registration.setSendBufferSizeLimit(8192);
        registration.setSendTimeLimit(10000);
    }

    /**
     * 配置消息代理（用于广播消息）
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 启用简单消息代理，用于广播消息和点对点消息
        registry.enableSimpleBroker("/topic", "/queue", "/user")
                // 配置心跳机制：保持WebSocket连接活跃
                .setHeartbeatValue(new long[] { 60000, 60000 }) // 客户端和服务器心跳间隔均为60秒
                .setTaskScheduler(taskScheduler()); // 设置任务调度器用于心跳任务

        // 设置应用程序消息前缀
        // 前端发送到/app/**的消息会被路由到带有@MessageMapping注解的方法
        registry.setApplicationDestinationPrefixes("/app");

        // 设置用户目的地前缀，用于点对点消息
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * 配置TaskScheduler用于WebSocket心跳任务
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10); // 线程池大小
        scheduler.setThreadNamePrefix("websocket-heartbeat-scheduler-"); // 线程名称前缀
        scheduler.setAwaitTerminationSeconds(60); // 关闭时等待任务完成的时间
        scheduler.setWaitForTasksToCompleteOnShutdown(true); // 关闭时是否等待任务完成
        scheduler.initialize(); // 初始化调度器
        return scheduler;
    }

    /**
     * 配置Redis消息监听：用于集群间的消息广播（分布式）
     */
    /*
     * @Bean
     * public RedisMessageListenerContainer redisMessageListenerContainer(
     * RedisConnectionFactory connectionFactory,
     * MessageListenerAdapter listenerAdapter) {
     * RedisMessageListenerContainer container = new
     * RedisMessageListenerContainer();
     * container.setConnectionFactory(connectionFactory);
     * container.addMessageListener(listenerAdapter, new
     * PatternTopic("/topic/public"));
     * return container;
     * }
     */

    // private final ChatWebSocketHandler chatHandler;

    // @Override
    // public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    // registry.addHandler(chatHandler, "/ws")
    // .addInterceptors(jwtInterceptor); // 注册JWT拦截器
    // }
}