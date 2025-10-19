// package com.guanyu.haigui.config;
//
// import org.springframework.context.annotation.Configuration;
// import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
// import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer;
//
// @Configuration
// public class WebSocketSecurityConfig extends AbstractSecurityWebSocketMessageBrokerConfigurer {
//
//     /**
//      * 配置STOMP消息的安全规则：
//      * - /app/chat.sendMessage：需要认证
//      * - /app/chat.addUser：需要认证（加入聊天室）
//      * - /topic/** /queue/**：允许匿名（或根据需求调整）
//      */
//     @Override
//     protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
//         messages
//                 .simpDestMatchers("/app/chat.sendMessage").authenticated() // 发送消息需认证
//                 .simpDestMatchers("/app/chat.addUser").authenticated() // 加入聊天室需认证
//                 .simpSubscribeDestMatchers("/topic/**", "/queue/**").permitAll() // 订阅允许匿名
//                 .anyMessage().denyAll(); // 其他消息拒绝
//     }
//
//     /**
//      * 允许握手时使用HTTP Session（可选，若用Session存储用户信息）
//      */
//     @Override
//     protected boolean sameOriginDisabled() {
//         return false; // 生产环境建议开启CSRF保护，设为false
//     }
// }