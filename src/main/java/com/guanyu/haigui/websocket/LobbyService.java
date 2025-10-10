package com.guanyu.haigui.websocket;

import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
@Data
@Service
public class LobbyService {
    // 存储大厅ID与成员用户ID的映射（线程安全）
    private final ConcurrentMap<String, Set<String>> lobbies = new ConcurrentHashMap<>();

    // 用户加入大厅
    public void joinLobby(String lobbyId, String userId) {
        lobbies.computeIfAbsent(lobbyId, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    // 用户离开大厅
    public void leaveLobby(String lobbyId, String userId) {
        Set<String> members = lobbies.get(lobbyId);
        if (members != null) {
            members.remove(userId);
            if (members.isEmpty()) {
                lobbies.remove(lobbyId);
            }
        }
    }

    // 获取大厅所有成员
    public Set<String> getLobbyMembers(String lobbyId) {
        return lobbies.getOrDefault(lobbyId, Collections.emptySet());
    }
}