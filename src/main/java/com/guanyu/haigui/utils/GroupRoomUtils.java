package com.guanyu.haigui.utils;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
@Component
public class GroupRoomUtils {
    private final ConcurrentMap<String, Set<Long>> roomMembers = new ConcurrentHashMap<>();


    // 添加用户到群组时，更新本地缓存
    public void addUserToGroup(String groupId, Long memberId) {
        roomMembers.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(memberId);
    }


    // 用户退出群组时，更新本地缓存
    public void leaveGroupRoom(String groupId, Long userId) {
        roomMembers.computeIfPresent(groupId, (key, members) -> {
            members.removeIf(id -> id.equals(userId));
            return members.isEmpty() ? null : members;
        });
    }

    // 获取群成员列表（优先本地缓存）
    public Set<Long> getGroupMembers(String groupId) {
        return roomMembers.getOrDefault(groupId, Collections.emptySet());
    }

}
