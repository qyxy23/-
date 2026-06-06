package com.guanyu.haigui.listenrer;

import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import com.guanyu.haigui.websocket.RoomService;
import com.guanyu.haigui.websocket.PrivateMessageSubscriptionRegistry;
import com.guanyu.haigui.websocket.StompUserPushService;
import com.guanyu.haigui.websocket.WebSocketUserSessionManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.UserDestinationResolver;
import org.springframework.messaging.simp.user.UserDestinationResult;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket事件监听器，集成用户会话管理和事件日志功能
 */
@Component
@Slf4j
public class WebSocketEventListener {

    @Value("${spring.profiles.active:prod}")
    private String active;

    @Resource
    private RoomService roomService;
    @Resource
    private SimpMessagingTemplate messagingTemplate;
    // 注入全局Map（存储sessionId -> 用户信息）
    @Resource
    private ConcurrentHashMap<String, CustomUserDetails> sessionUserMap;
    @Resource
    private WebSocketUserSessionManager webSocketUserSessionManager;
    @Resource
    private PrivateMessageSubscriptionRegistry privateMessageSubscriptionRegistry;
    @Resource
    private UserDestinationResolver userDestinationResolver;

    /**
     * 该event方法是后端返回的响应，用于处理用户会话关联
     * 监听WebSocket连接建立事件，同时处理用户会话关联
     * 注意：此方法会在STOMP CONNECT帧处理完成后被自动调用
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        // if ("dev".equals(active)){
        // log.info("===== STOMP SessionConnectedEvent 被触发 =====");
        // log.info("当前环境为开发环境，不处理用户会话关联");
        // SimpMessageHeaderAccessor headers =
        // SimpMessageHeaderAccessor.wrap(event.getMessage());
        // String sessionId = headers.getSessionId();
        // log.info("STOMP会话连接已建立: sessionId={}", sessionId);
        // return;
        // }
        log.info("===== STOMP SessionConnectedEvent 被触发 =====");
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        CustomUserDetails user = sessionId != null ? sessionUserMap.get(sessionId) : null;
        if (user != null) {
            log.info("STOMP 会话已建立: sessionId={}, userId={}, username={}",
                    sessionId, user.getUserId(), user.getUsername());
        } else {
            log.warn("STOMP 会话已建立但未找到用户: sessionId={}", sessionId);
        }
        log.info("===== STOMP SessionConnectedEvent 处理完毕 =====");
    }

    /**
     * 监听STOMP订阅事件，这是客户端订阅主题的确认
     */
    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        String destination = headers.getDestination();

        CustomUserDetails customUserDetails = sessionId != null ? sessionUserMap.get(sessionId) : null;
        if (destination != null && destination.contains("private-messages")) {
            if (customUserDetails != null) {
                String brokerDestination = destination.startsWith("/queue/")
                        ? destination
                        : resolveBrokerDestination(sessionId);
                privateMessageSubscriptionRegistry.register(
                        customUserDetails.getUserId(), sessionId, brokerDestination);
                log.info("私聊订阅已记录: userId={}, sessionId={}, brokerDestination={}",
                        customUserDetails.getUserId(), sessionId, brokerDestination);
            } else {
                log.warn("私聊订阅未记录（无用户信息）: sessionId={}, destination={}", sessionId, destination);
            }
        }
    }

    /**
     * 监听WebSocket断开连接事件，同时清理用户会话和大厅相关资源
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        // if ("dev".equals(active)){
        // log.info("===== WebSocket断开连接事件被触发 =====");
        // log.info("当前环境为开发环境，不执行清理逻辑");
        // String sessionId = event.getSessionId();
        // log.info("WebSocket连接已断开: sessionId={}", sessionId);
        // log.info("===== WebSocket断开连接事件处理完毕 =====");
        // return;
        // }
        log.info("===== WebSocket断开连接事件被触发 =====");

        String sessionId = event.getSessionId();
        log.info("WebSocket连接已断开: sessionId={}", sessionId);

        try {
            // 从全局Map中获取用户信息
            CustomUserDetails customUserDetails = sessionUserMap.get(sessionId);
            if (customUserDetails != null) {
                log.info("用户信息: {}", customUserDetails);
            } else {
                log.warn("未找到用户信息，无法清理用户会话");
            }

            // 清理用户会话关联
            if (sessionUserMap.containsKey(sessionId)) {
                sessionUserMap.remove(sessionId);
                log.info("用户 {} 断开连接，sessionId [{}] 已移除",
                        customUserDetails != null ? customUserDetails.getUsername() : "未知用户", sessionId);
            }
            webSocketUserSessionManager.unregister(sessionId);
            privateMessageSubscriptionRegistry.unregisterSession(sessionId);

            // 如果获取到用户信息，执行大厅清理逻辑
            // if (customUserDetails != null) {
            // String userId = customUserDetails.getUserId().toString();
            // log.info("用户 {} 断开连接，开始清理大厅资源", userId);
            //
            // // 遍历所有大厅，移除该用户
            // roomService.getLobbies().forEach((lobbyId, members) -> {
            // if (members.contains(userId)) {
            // log.info("从大厅 {} 移除用户 {}", lobbyId, userId);
            // members.remove(userId);
            //
            // // 通知大厅成员"用户离开"
            // try {
            // ChatMessage chatMessage = new ChatMessage();
            // chatMessage.setRole(ChatMessageRole.SYSTEM);
            // chatMessage.setContent(userId + " 离开了大厅");
            // messagingTemplate.convertAndSend("/topic/chat/" + lobbyId, chatMessage);
            // log.info("向大厅 {} 发送离开通知", lobbyId);
            // } catch (Exception e) {
            // log.error("发送离开通知失败：{}", e.getMessage(), e);
            // }
            // }
            // });
            // }
            log.info("===== WebSocket断开连接事件处理完毕 =====");
        } catch (Exception e) {
            log.error("处理会话断开异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 监听取消订阅事件
     */
    @EventListener
    public void handleSessionUnsubscribeEvent(SessionUnsubscribeEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        String destination = headers.getDestination();

        // 获取用户信息以便在日志中显示用户名
        UserInfo userInfo = sessionId != null ? sessionUserMap.get(sessionId) : null;
        String username = userInfo != null ? userInfo.getUsername() : "未知用户";

        log.info("用户 {} 取消订阅了主题: sessionId={}, destination={}", username, sessionId, destination);
    }

    private String resolveBrokerDestination(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setDestination("/user" + StompUserPushService.PRIVATE_MESSAGE_DEST);
        accessor.setLeaveMutable(true);
        UserDestinationResult result = userDestinationResolver.resolveDestination(
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
        if (result == null || result.getTargetDestinations().isEmpty()) {
            return null;
        }
        return result.getTargetDestinations().iterator().next();
    }
}