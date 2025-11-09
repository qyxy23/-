package com.guanyu.haigui.utils;

import lombok.AllArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@AllArgsConstructor
@Component
public class LobbyRoomUtils {
    private final ConcurrentMap<String, Set<Long>> LobbyMembers = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate simpMessagingTemplate;

    // 添加用户到群组时，更新本地缓存
    public void addUserToLobby(String LobbyId, Long memberId) {
        LobbyMembers.computeIfAbsent(LobbyId, k -> ConcurrentHashMap.newKeySet()).add(memberId);
    }

    // 用户退出群组时，更新本地缓存
    public void leaveLobbyRoom(String LobbyId, Long userId) {
        LobbyMembers.computeIfPresent(LobbyId, (key, members) -> {
            members.removeIf(id -> id.equals(userId));
            return members.isEmpty() ? null : members;
        });
    }

    // 获取群成员列表（优先本地缓存）
    public Set<Long> getLobbyMembers(String LobbyId) {
        return LobbyMembers.getOrDefault(LobbyId, Collections.emptySet());
    }

    /**
     * 向群成员广播消息（给每个成员发私信）
     *
     * @param messageVo 要发送的消息VO
     * @param groupId   群聊ID
     */
    public void broadcastGroupMessageToMembers(Object messageVo, String groupId) {
        // 获取群成员列表（可根据需要加缓存）
        Set<Long> groupMembers = getLobbyMembers(groupId);
        // 遍历发送给每个成员
        groupMembers.forEach(memberId -> {
            simpMessagingTemplate.convertAndSendToUser(
                    String.valueOf(memberId), // 目标用户ID（Stomp会自动拼接成/user/{memberId}/private-messages）
                    "/private-messages", // 订阅路径（客户端需要订阅此路径才能收到消息）
                    messageVo // 要发送的消息内容
            );
        });
    }
}