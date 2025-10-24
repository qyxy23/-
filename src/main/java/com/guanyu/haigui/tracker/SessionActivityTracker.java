package com.guanyu.haigui.tracker;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionActivityTracker {
    // 存储会话ID → 最后活跃时间（毫秒）
    private final Map<String, Long> sessionLastActive = new ConcurrentHashMap<>();

    /**
     * 更新会话的最后活跃时间
     * @param sessionId 会话ID
     */
    public void updateLastActive(String sessionId) {
        sessionLastActive.put(sessionId, System.currentTimeMillis());
    }

    /**
     * 获取会话的最后活跃时间
     * @param sessionId 会话ID
     * @return 时间戳（null表示会话不存在）
     */
    public Long getLastActiveTime(String sessionId) {
        return sessionLastActive.get(sessionId);
    }

    /**
     * 移除会话
     * @param sessionId 会话ID
     */
    public void removeSession(String sessionId) {
        sessionLastActive.remove(sessionId);
    }

    /**
     * 获取所有超时的不活跃会话ID
     * @param timeout 超时时间（毫秒）
     * @return 超时会话ID列表
     */
    public java.util.List<String> getInactiveSessionIds(long timeout) {
        long currentTime = System.currentTimeMillis();
        return sessionLastActive.entrySet().stream()
                .filter(entry -> currentTime - entry.getValue() > timeout)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }
}