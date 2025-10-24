// package com.guanyu.haigui.config;
//
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.messaging.Message;
// import org.springframework.security.authorization.AuthorizationManager;
// import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
// import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
//
// import static org.springframework.messaging.simp.SimpMessageType.*;
//
// @Configuration
// @EnableWebSocketSecurity
// public class WebSocketSecurityConfig {
//
//     /**
//      * 配置STOMP消息的安全规则
//      * 关键：允许CONNECT帧通过，在CONNECT帧处理时设置Principal和SecurityContext
//      */
//     @Bean
//     public MessageMatcherDelegatingAuthorizationManager.Builder
//     customWebSocketAuthorizationManagerBuilder() {
//         MessageMatcherDelegatingAuthorizationManager.Builder messages =
//                 MessageMatcherDelegatingAuthorizationManager
//                         .builder();
//
// // 允许认证用户访问所有应用消息
//         messages.simpDestMatchers("/app/**", "/createLobby").authenticated();
//
// // 关键：首先允许所有STOMP命令类型通过，包括CONNECT
//         messages.simpTypeMatchers(CONNECT, SUBSCRIBE, UNSUBSCRIBE, DISCONNECT,
//                 MESSAGE).permitAll();
//
// // 允许订阅广播和队列主题
//         messages.simpSubscribeDestMatchers("/topic/**", "/queue/**").permitAll();
//
//         return messages;
//     }
//
//     /**
//      * 创建并返回实际的AuthorizationManager Bean
//      */
//     @Bean
//     public AuthorizationManager<Message<?>> authorizationManager() {
//         return customWebSocketAuthorizationManagerBuilder().build();
//     }
//
// }