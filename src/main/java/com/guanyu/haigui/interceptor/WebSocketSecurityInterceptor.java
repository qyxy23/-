package com.guanyu.haigui.interceptor;

import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import com.guanyu.haigui.tracker.SessionActivityTracker;
import com.guanyu.haigui.websocket.WebSocketUserSessionManager;
import com.guanyu.haigui.websocket.StompSubscriptionAuthorizer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 合并后的WebSocket安全拦截器
 * 功能：
 * 1. 跟踪会话活动（从ActivityTrackingInterceptor）
 * 2. 在STOMP CONNECT帧中验证JWT Token
 */
@Component
@Slf4j
public class WebSocketSecurityInterceptor implements ChannelInterceptor {
    @Value("${spring.profiles.active:prod}")
    private String active;

    @Resource
    private SessionActivityTracker sessionActivityTracker;
    // 注入全局Map（存储sessionId -> 用户信息）
    @Resource
    private ConcurrentHashMap<String, CustomUserDetails> sessionUserMap;
    @Resource
    private WebSocketUserSessionManager webSocketUserSessionManager;
    @Resource
    private StompSubscriptionAuthorizer stompSubscriptionAuthorizer;

    @Override
    public Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        String sessionId = accessor.getSessionId();

        log.info("===== STOMP 消息预处理 =====");
        log.info("命令: {}, 会话ID: {}", command == null ? "心跳帧" : command, sessionId);

        try {
            // ------------------- 1. 处理 CONNECT 帧（核心：设置用户身份） -------------------
            if (StompCommand.CONNECT.equals(command)) {
                // 传入原始消息，处理成功时返回原始消息以允许连接
                return DealWithConnectFrame(accessor, message); // 校验成功，返回原始消息以建立连接
            }
            // 无论是否为心跳帧，都更新会话活跃时间
            updateSessionActivity(sessionId);

            if (StompCommand.SUBSCRIBE.equals(command)) {
                String destination = accessor.getDestination();
                Long userId = resolveUserId(accessor, sessionId);
                if (!stompSubscriptionAuthorizer.canSubscribe(userId, destination)) {
                    log.warn("拒绝 SUBSCRIBE：userId={}, destination={}, sessionId={}", userId, destination, sessionId);
                    return null;
                }
                log.debug("SUBSCRIBE 已授权：userId={}, destination={}", userId, destination);
                return message;
            }

            if ("dev".equals(active)) {
                log.debug("开发环境：跳过非 CONNECT/SUBSCRIBE 帧的验证");
                return message;
            }

            return message;
        } catch (Exception e) {
            log.error("STOMP 消息处理异常：命令={}, 会话ID={}, 错误={}", command, sessionId, e.getMessage(), e);
            return null; // 异常时拒绝消息
        }
    }

    /**
     * 处理 CONNECT 帧（核心：验证身份 + 设置上下文）
     */
    private Message<?> DealWithConnectFrame(StompHeaderAccessor accessor, Message<?> originalMessage) {
        String sessionId = accessor.getSessionId();
        log.info("开始处理 CONNECT 帧（STOMP 握手），会话ID: {}", sessionId);

        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs == null || sessionAttrs.isEmpty()) {
            log.warn("会话未初始化（无属性），拒绝 CONNECT，会话ID: {}", sessionId);
            return rejectConnection(accessor, "会话未初始化");
        }

        Principal principal = (Principal) sessionAttrs.get(Principal.class.getName());
        if (principal == null) {
            log.warn("未找到 Principal（认证信息），拒绝 CONNECT，会话ID: {}", sessionId);
            return rejectConnection(accessor, "无认证信息");
        }

        if (!(principal instanceof CustomUserDetails customUserDetails)) {
            String errorMsg = String.format(
                    "Principal 类型错误，预期 CustomUserDetails，实际：%s，会话ID: %s",
                    principal.getClass().getName(), sessionId);
            log.warn(errorMsg);
            return rejectConnection(accessor, errorMsg);
        }

        log.info(
                "CONNECT 帧验证通过：用户名={}, 角色={}, 会话ID={}",
                customUserDetails.getName(),
                customUserDetails.getAuthorities(),
                sessionId);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                customUserDetails,
                null,
                customUserDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        accessor.setUser(customUserDetails);
        accessor.setLeaveMutable(true);

        if (StringUtils.hasText(sessionId)) {
            sessionUserMap.put(sessionId, customUserDetails);
            webSocketUserSessionManager.register(customUserDetails.getUserId(), sessionId);
            log.info("STOMP 用户已注册: userId={}, username={}, sessionId={}",
                    customUserDetails.getUserId(),
                    customUserDetails.getUsername(),
                    sessionId);
        } else {
            log.warn("sessionId 为空，无法存入 sessionUserMap");
        }

        return MessageBuilder.createMessage(originalMessage.getPayload(), accessor.getMessageHeaders());
    }

    private Long resolveUserId(StompHeaderAccessor accessor, String sessionId) {
        if (accessor.getUser() instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        CustomUserDetails cached = sessionUserMap.get(sessionId);
        return cached != null ? cached.getUserId() : null;
    }

    private void updateSessionActivity(String sessionId) {
        long currentTime = System.currentTimeMillis();
        sessionActivityTracker.updateLastActive(sessionId);

        // // 记录活跃时间更新，便于调试
        if (log.isDebugEnabled()) {
            log.debug("会话 {} 更新于 {}", sessionId, currentTime);
        }
    }

    /**
     * 拒绝连接（返回 null 时 Spring 会终止连接）
     */
    private Message<?> rejectConnection(StompHeaderAccessor accessor, String reason) {
        log.warn("拒绝 CONNECT 请求：{}", reason);
        accessor.setHeader("rejectReason", reason); // 可选：传递拒绝原因（客户端可能无法直接获取）
        return null;
    }

    /**
     * 清理资源（可选：长连接场景需谨慎，避免提前清除 Context）
     */
    // @Override
    // public void afterSendCompletion(Message<?> message, MessageChannel channel,
    // boolean sent, Exception ex) {
    // // 清除当前线程的 SecurityContext（若连接断开，可避免内存泄漏）
    // SecurityContextHolder.clearContext();
    // log.info("STOMP 消息发送完成，清理 SecurityContext");
    // }

    @Override
    public void postSend(@NotNull Message<?> message, @NotNull MessageChannel channel, boolean sent) {
        // 记录消息发送后的处理
        if (!sent) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            String sessionId = accessor.getSessionId();
            log.warn("Message not sent successfully for session: {}", sessionId);
        }
    }
}