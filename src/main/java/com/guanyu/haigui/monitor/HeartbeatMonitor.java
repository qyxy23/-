package com.guanyu.haigui.monitor;

import com.guanyu.haigui.tracker.SessionActivityTracker;
import com.guanyu.haigui.utils.CustomSessionRegistry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
@AllArgsConstructor
@Component
@Slf4j
public class HeartbeatMonitor {

    private final CustomSessionRegistry sessionRegistry;
    private final SessionActivityTracker sessionActivityTracker;
    // 定时检查间隔（60秒）- 比心跳间隔长，避免频繁检查
    private static final long CHECK_INTERVAL = 60000;
    // 会话超时时间（180秒无活动则断开）- 明显长于STOMP心跳间隔(10秒)，避免错误关闭活跃会话
    private static final long SESSION_TIMEOUT = 180000;


    /**
     * 定时任务：检查并关闭不活跃会话
     */
    @Scheduled(fixedRate = CHECK_INTERVAL)
    public void closeInactiveSessions() {
        log.info("定时任务:检查并关闭不活跃的会话");
        List<String> inactiveSessionIds = sessionActivityTracker.getInactiveSessionIds(SESSION_TIMEOUT);
        inactiveSessionIds.forEach(sessionId -> {
            WebSocketSession session = sessionRegistry.getSession(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    // 关闭会话（原因：长时间无活动）
                    log.info("Closing inactive session after {}ms of inactivity: {}", SESSION_TIMEOUT, sessionId);
                    session.close(CloseStatus.SESSION_NOT_RELIABLE.withReason("No activity for " + SESSION_TIMEOUT + "ms"));
                    sessionActivityTracker.removeSession(sessionId);
                    log.info("Successfully closed inactive session: {}", sessionId);
                } catch (IOException e) {
                    log.error("Failed to close session {}: {}", sessionId, e.getMessage(), e);
                }
            } else if (session != null) {
                log.warn("Session {} is already closed, removing from tracker", sessionId);
                sessionActivityTracker.removeSession(sessionId);
            } else {
                log.warn("Session {} not found in registry, removing from tracker", sessionId);
                sessionActivityTracker.removeSession(sessionId);
            }
        });
    }
}