package com.guanyu.haigui.service;

import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.model.ChatGame;
import com.guanyu.haigui.pojo.vo.LobbyShareTokenVO;
import com.guanyu.haigui.repository.ChatGameMemberRepository;
import com.guanyu.haigui.repository.ChatGameRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class LobbyShareTokenService {

    private static final String TOKEN_PREFIX = "lobby:share:token:";
    private static final long TTL_HOURS = 24;

    private final RedisTemplate<String, String> redisTemplate;
    private final ChatGameRepository chatGameRepository;
    private final ChatGameMemberRepository chatGameMemberRepository;
    private final UserInfoRepository userInfoRepository;

    public LobbyShareTokenVO createOrRefresh(String roomId) {
        Long userId = BaseContext.getCurrentId();
        ChatGame room = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(404, "房间不存在"));
        var user = userInfoRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(403, "用户不存在"));
        if (!chatGameMemberRepository.existsByChatGameAndMember(room, user)) {
            throw new BusinessException(403, "仅房间成员可生成分享链接");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        long ttlMillis = TimeUnit.HOURS.toMillis(TTL_HOURS);
        long expiresAt = System.currentTimeMillis() + ttlMillis;
        redisTemplate.opsForValue().set(TOKEN_PREFIX + token, roomId, TTL_HOURS, TimeUnit.HOURS);

        LobbyShareTokenVO vo = new LobbyShareTokenVO();
        vo.setRoomId(room.getRoomId());
        vo.setShareToken(token);
        vo.setExpiresAt(expiresAt);
        return vo;
    }

    public boolean isValid(String roomId, String shareToken) {
        if (!StringUtils.hasText(roomId) || !StringUtils.hasText(shareToken)) {
            return false;
        }
        String boundRoomId = redisTemplate.opsForValue().get(TOKEN_PREFIX + shareToken);
        return roomId.equals(boundRoomId);
    }
}
