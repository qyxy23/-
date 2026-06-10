package com.guanyu.haigui.utils;

import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.pojo.model.ChatGame;
import com.guanyu.haigui.repository.ChatGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 多人大厅 roomId 与玩法 gameSessionId 的桥接。
 */
@Component
@RequiredArgsConstructor
public class GameSessionResolver {

    private final ChatGameRepository chatGameRepository;

    public String requireGameSessionId(String roomId) {
        ChatGame game = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(404, "房间不存在：ID=" + roomId));
        if (game.getGameSessionId() == null || game.getGameSessionId().isBlank()) {
            throw new BusinessException(403, "游戏未开始：ID=" + roomId);
        }
        return game.getGameSessionId();
    }

    public String requireRoomId(String gameSessionId) {
        return chatGameRepository.findFirstByGameSessionId(gameSessionId)
                .map(ChatGame::getRoomId)
                .orElseThrow(() -> new BusinessException(404, "未找到关联大厅，gameSessionId=" + gameSessionId));
    }

    public ChatGame requireChatGame(String gameSessionId) {
        return chatGameRepository.findFirstByGameSessionId(gameSessionId)
                .orElseThrow(() -> new BusinessException(404, "未找到关联大厅，gameSessionId=" + gameSessionId));
    }
}
