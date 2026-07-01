package com.guanyu.haigui.websocket;

import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.repository.ChatGameMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 游戏大厅成员权限校验（订阅、发消息、拉历史等共用）
 */
@Service
@RequiredArgsConstructor
public class LobbyAccessService {

    private final ChatGameMemberRepository chatGameMemberRepository;

    public boolean isMember(Long userId, String roomId) {
        if (userId == null || roomId == null || roomId.isBlank()) {
            return false;
        }
        return chatGameMemberRepository.findByRoomIdAndUserId(roomId.trim(), userId).isPresent();
    }

    public void assertMember(Long userId, String roomId) {
        if (!isMember(userId, roomId)) {
            throw new BusinessException(403, "您不是该房间成员，无权操作");
        }
    }
}
