package com.guanyu.haigui.websocket;

import com.guanyu.haigui.repository.ChatGameRepository;
import com.guanyu.haigui.repository.ChatGroupMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP SUBSCRIBE 目的地鉴权：仅允许订阅本人私聊频道或已加入的大厅/群聊主题。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StompSubscriptionAuthorizer {

    private static final Pattern MEMBER_CHANGE = Pattern.compile("^/topic/memberChange(.+)$");
    private static final Pattern RECENT_TOPIC = Pattern.compile("^/topic/recent/([^/]+)$");
    private static final Pattern PRIVATE_CHAT = Pattern.compile("^/topic/private-chat\\.(\\d+)$");

    private final LobbyAccessService lobbyAccessService;
    private final ChatGameRepository chatGameRepository;
    private final ChatGroupMemberRepository chatGroupMemberRepository;

    public boolean canSubscribe(Long userId, String destination) {
        if (userId == null || !StringUtils.hasText(destination)) {
            return false;
        }
        String dest = destination.trim();

        // Spring 用户专属队列（/user/queue/...），仅已认证用户可订阅，由 broker 按 Principal 隔离
        if (dest.startsWith("/user/")) {
            return true;
        }

        Matcher memberChange = MEMBER_CHANGE.matcher(dest);
        if (memberChange.matches()) {
            return lobbyAccessService.isMember(userId, memberChange.group(1));
        }

        Matcher privateChat = PRIVATE_CHAT.matcher(dest);
        if (privateChat.matches()) {
            return userId.equals(parseUserId(privateChat.group(1)));
        }

        Matcher recent = RECENT_TOPIC.matcher(dest);
        if (recent.matches()) {
            return canAccessRecentTopic(userId, recent.group(1));
        }

        if ("/topic/searchLobbies_result".equals(dest)) {
            return true;
        }

        log.warn("拒绝未知 STOMP 订阅: userId={}, destination={}", userId, dest);
        return false;
    }

    private boolean canAccessRecentTopic(Long userId, String sessionId) {
        if (chatGameRepository.existsById(sessionId)) {
            return lobbyAccessService.isMember(userId, sessionId);
        }
        return chatGroupMemberRepository.existsByChatGroupGroupIdAndMemberUserId(sessionId, userId);
    }

    private static Long parseUserId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
