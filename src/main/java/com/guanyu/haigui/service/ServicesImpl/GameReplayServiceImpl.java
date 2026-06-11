package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.Enum.PlayMode;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.config.ReplayCacheProperties;
import com.guanyu.haigui.pojo.dto.ReplayBuildHints;
import com.guanyu.haigui.pojo.model.ChatGame;
import com.guanyu.haigui.pojo.model.GameSession;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.result.GameSettlementSnapshot;
import com.guanyu.haigui.pojo.vo.EndGameVO;
import com.guanyu.haigui.pojo.vo.getAIChatListDetailVO;
import com.guanyu.haigui.repository.ChatGameMemberRepository;
import com.guanyu.haigui.repository.ChatGameRepository;
import com.guanyu.haigui.repository.GameSessionRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.GameReplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameReplayServiceImpl implements GameReplayService {

    private static final String KEY_PREFIX = "game:replay:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ReplayCacheProperties replayCacheProperties;
    private final ChatGameMemberRepository chatGameMemberRepository;
    private final ChatGameRepository chatGameRepository;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final GameSessionRepository gameSessionRepository;
    private final UserInfoRepository userInfoRepository;
    private final GameSettlementBuilder gameSettlementBuilder;
    private final GameHistoryBuilder gameHistoryBuilder;

    @Override
    public getAIChatListDetailVO getDetailForUser(String roomId, String gameSessionId, Long userId) {
        if (gameSessionId != null && !gameSessionId.isBlank()
                && (roomId == null || roomId.isBlank())) {
            return getSoloDetailForUser(gameSessionId.trim(), userId);
        }
        if (roomId == null || roomId.isBlank()) {
            throw new BusinessException(400, "请指定 roomId 或 gameSessionId");
        }
        return getMultiDetailForUser(roomId.trim(), userId);
    }

    @Override
    public void attachReplayAtEnd(EndGameVO endGameVO, String gameSessionId, String roomId, Long soloUserId) {
        attachReplayAtEnd(endGameVO, gameSessionId, roomId, soloUserId, null);
    }

    @Override
    public void attachReplayAtEnd(EndGameVO endGameVO, String gameSessionId, String roomId, Long soloUserId,
                                  ReplayBuildHints hints) {
        getAIChatListDetailVO replay = getOrBuildAndCache(gameSessionId, roomId, soloUserId, hints);
        endGameVO.setReplayDetail(replay);
    }

    @Override
    public getAIChatListDetailVO getOrBuildAndCache(String gameSessionId, String roomId, Long soloUserId) {
        return getOrBuildAndCache(gameSessionId, roomId, soloUserId, null);
    }

    @Override
    public getAIChatListDetailVO getOrBuildAndCache(String gameSessionId, String roomId, Long soloUserId,
                                                  ReplayBuildHints hints) {
        Optional<getAIChatListDetailVO> cached = getCached(gameSessionId);
        if (cached.isPresent()) {
            return cached.get();
        }
        getAIChatListDetailVO built = (roomId != null && !roomId.isBlank())
                ? buildMultiDetail(roomId, hints)
                : buildSoloDetail(gameSessionId, soloUserId, hints);
        putCache(gameSessionId, built);
        return built;
    }

    private getAIChatListDetailVO getMultiDetailForUser(String roomId, Long userId) {
        chatGameMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(403, "无权查看该对局"));

        ChatGame chatGame = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(404, "房间不存在"));
        String sessionId = chatGame.getGameSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(400, "该对局尚未开始");
        }

        Optional<getAIChatListDetailVO> cached = getCached(sessionId);
        if (cached.isPresent()) {
            return cached.get();
        }
        getAIChatListDetailVO built = buildMultiDetail(roomId, null);
        putCache(sessionId, built);
        return built;
    }

    private getAIChatListDetailVO getSoloDetailForUser(String gameSessionId, Long userId) {
        GameSession session = gameSessionRepository.findById(gameSessionId)
                .orElseThrow(() -> new BusinessException(404, "游戏会话不存在"));
        if (session.getPlayMode() != PlayMode.SOLO) {
            throw new BusinessException(400, "非单人游戏会话");
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(403, "无权查看该对局");
        }
        if (session.getStatus() == GameSession.GameSessionStatus.ONGOING) {
            throw new BusinessException(400, "游戏尚未结束");
        }

        Optional<getAIChatListDetailVO> cached = getCached(gameSessionId);
        if (cached.isPresent()) {
            return cached.get();
        }
        getAIChatListDetailVO built = buildSoloDetail(gameSessionId, userId, null);
        putCache(gameSessionId, built);
        return built;
    }

    private getAIChatListDetailVO buildMultiDetail(String roomId, ReplayBuildHints hints) {
        GameSettlementSnapshot snapshot = resolveSnapshot(hints, () -> gameSettlementBuilder.build(roomId));

        String soupId;
        String soupSurface;
        LocalDateTime endTime;
        String gameSessionId = snapshot.getGameSessionId();

        if (hasContext(hints)) {
            soupId = hints.getSoupId();
            soupSurface = hints.getSoupSurface();
            endTime = hints.getEndTime();
        } else {
            ChatGame chatGame = chatGameRepository.findById(roomId)
                    .orElseThrow(() -> new BusinessException(404, "房间不存在"));
            HaiGuiSoup soup = haiGuiSoupRepository.findById(chatGame.getHaiGuiSoup().getSoupId())
                    .orElseThrow(() -> new BusinessException(404, "该海龟汤不存在"));
            soupId = soup.getSoupId();
            soupSurface = soup.getSoupSurface();
            endTime = chatGame.getEndTime();
            if (gameSessionId == null) {
                gameSessionId = chatGame.getGameSessionId();
            }
        }

        getAIChatListDetailVO detail = getAIChatListDetailVO.fromSnapshot(snapshot);
        detail.setPlayMode(PlayMode.MULTI.name());
        detail.setRoomId(roomId);
        detail.setSoupSurface(soupSurface);
        detail.setEndTime(endTime);

        GameHistoryBuilder.HistoryBundle history =
                gameHistoryBuilder.buildForSession(roomId, soupId, gameSessionId);
        detail.setQuestions(history.getQuestions());
        detail.setMembers(history.getMembers());
        detail.setTimeline(history.getTimeline());
        detail.setMvpUserId(history.getMvpUserId());
        return detail;
    }

    private getAIChatListDetailVO buildSoloDetail(String gameSessionId, Long userId, ReplayBuildHints hints) {
        GameSettlementSnapshot snapshot = resolveSnapshot(
                hints, () -> gameSettlementBuilder.buildByGameSessionId(gameSessionId));

        String soupId;
        String soupSurface;
        LocalDateTime endTime;
        UserInfo player;

        if (hasContext(hints)) {
            soupId = hints.getSoupId();
            soupSurface = hints.getSoupSurface();
            endTime = hints.getEndTime();
            player = userInfoRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        } else {
            GameSession session = gameSessionRepository.findById(gameSessionId)
                    .orElseThrow(() -> new BusinessException(404, "游戏会话不存在"));
            HaiGuiSoup soup = haiGuiSoupRepository.findById(session.getSoupId())
                    .orElseThrow(() -> new BusinessException(404, "该海龟汤不存在"));
            soupId = soup.getSoupId();
            soupSurface = soup.getSoupSurface();
            endTime = session.getEndTime();
            player = userInfoRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        }

        getAIChatListDetailVO detail = getAIChatListDetailVO.fromSnapshot(snapshot);
        detail.setPlayMode(PlayMode.SOLO.name());
        detail.setGameSessionId(gameSessionId);
        detail.setSoupSurface(soupSurface);
        detail.setEndTime(endTime);

        GameHistoryBuilder.HistoryBundle history =
                gameHistoryBuilder.buildSolo(gameSessionId, soupId, player);
        detail.setQuestions(history.getQuestions());
        detail.setMembers(history.getMembers());
        detail.setTimeline(history.getTimeline());
        detail.setMvpUserId(history.getMvpUserId());
        return detail;
    }

    private static GameSettlementSnapshot resolveSnapshot(
            ReplayBuildHints hints,
            java.util.function.Supplier<GameSettlementSnapshot> fallback) {
        if (hints != null && hints.getSnapshot() != null) {
            return hints.getSnapshot();
        }
        return fallback.get();
    }

    private static boolean hasContext(ReplayBuildHints hints) {
        return hints != null && hints.getSoupId() != null && !hints.getSoupId().isBlank();
    }

    private Optional<getAIChatListDetailVO> getCached(String gameSessionId) {
        if (gameSessionId == null || gameSessionId.isBlank()) {
            return Optional.empty();
        }
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + gameSessionId);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, getAIChatListDetailVO.class));
        } catch (JsonProcessingException e) {
            log.warn("复盘缓存反序列化失败 gameSessionId={}", gameSessionId, e);
            redisTemplate.delete(KEY_PREFIX + gameSessionId);
            return Optional.empty();
        }
    }

    private void putCache(String gameSessionId, getAIChatListDetailVO detail) {
        if (gameSessionId == null || gameSessionId.isBlank() || detail == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(detail);
            long ttlDays = Math.max(1, replayCacheProperties.getTtlDays());
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + gameSessionId,
                    json,
                    ttlDays,
                    TimeUnit.DAYS
            );
        } catch (JsonProcessingException e) {
            log.warn("复盘缓存序列化失败 gameSessionId={}", gameSessionId, e);
        }
    }
}
