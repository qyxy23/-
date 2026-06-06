package com.guanyu.haigui.websocket;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 记录用户私聊频道订阅对应的 broker 目的地（/queue/private-messages-userxxx）。
 */
@Component
public class PrivateMessageSubscriptionRegistry {

    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<String>> userDestinations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionDestination = new ConcurrentHashMap<>();

    public void register(Long userId, String sessionId, String brokerDestination) {
        if (userId == null || sessionId == null || brokerDestination == null || brokerDestination.isBlank()) {
            return;
        }
        userDestinations.computeIfAbsent(userId, ignored -> new CopyOnWriteArraySet<>()).add(brokerDestination);
        sessionDestination.put(sessionId, brokerDestination);
    }

    public void unregisterSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        String brokerDestination = sessionDestination.remove(sessionId);
        if (brokerDestination == null) {
            return;
        }
        userDestinations.values().forEach(set -> set.remove(brokerDestination));
        userDestinations.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public Set<String> getBrokerDestinations(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        CopyOnWriteArraySet<String> destinations = userDestinations.get(userId);
        return destinations == null ? Set.of() : Collections.unmodifiableSet(destinations);
    }
}
