package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.ChatContextType;
import com.guanyu.haigui.Enum.GameEndReason;
import com.guanyu.haigui.Enum.PlayMode;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.ReplayBuildHints;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.result.GameSettlementSnapshot;
import com.guanyu.haigui.pojo.vo.EndGameVO;
import com.guanyu.haigui.pojo.vo.OngoingSoloVO;
import com.guanyu.haigui.pojo.vo.RoomGetClueVO;
import com.guanyu.haigui.pojo.vo.StartSoloVO;
import com.guanyu.haigui.repository.*;
import com.guanyu.haigui.service.GameReplayService;
import com.guanyu.haigui.service.PlayQuotaService;
import com.guanyu.haigui.service.SoloGameService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SoloGameServiceImpl implements SoloGameService {

    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final GameSessionRepository gameSessionRepository;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final InferenceTaskRepository inferenceTaskRepository;
    private final HaiGuiGameProgressRepository haiGuiGameProgressRepository;
    private final HaiGuiChatMessageRepository haiGuiChatMessageRepository;
    private final SoupQuestionServiceImpl soupQuestionService;
    private final GameSettlementBuilder gameSettlementBuilder;
    private final PlayQuotaService playQuotaService;
    private final GameReplayService gameReplayService;
    private final SoupPlayabilityService soupPlayabilityService;
    private final GameProgressEnricher gameProgressEnricher;

    @Override
    public StartSoloVO startSolo(String soupId) {
        Long userId = BaseContext.getCurrentId();

        var ongoing = gameSessionRepository
                .findFirstByUserIdAndSoupIdAndPlayModeAndStatusAndIsDeletedFalseOrderByStartTimeDesc(
                        userId, soupId, PlayMode.SOLO, GameSession.GameSessionStatus.ONGOING);
        if (ongoing.isPresent()) {
            HaiGuiSoup existingSoup = haiGuiSoupRepository.findById(soupId)
                    .orElseThrow(() -> new BusinessException(404, "海龟汤不存在"));
            return toStartSoloVO(ongoing.get(), existingSoup, true);
        }

        HaiGuiSoup soup = soupPlayabilityService.requirePlayableSoup(soupId);

        playQuotaService.assertCanStartNewGame(userId);

        String gameSessionId = UUID.randomUUID().toString();

        AiChatSession aiSession = new AiChatSession();
        aiSession.setSessionId(UUID.randomUUID().toString());
        aiSession.setUserId(userId);
        aiSession.setTitle(soup.getSoupTitle());
        aiSession.setContextType(ChatContextType.AI_SOLO);
        aiSession.setContextId(gameSessionId);
        aiSession.setDeleted(false);
        aiChatSessionRepository.save(aiSession);

        GameSession gameSession = new GameSession();
        gameSession.setSessionId(gameSessionId);
        gameSession.setSoupId(soupId);
        gameSession.setUserId(userId);
        gameSession.setPlayMode(PlayMode.SOLO);
        gameSession.setRoomId(null);
        gameSession.setChatSessionId(aiSession.getSessionId());
        gameSession.setStatus(GameSession.GameSessionStatus.ONGOING);
        gameSession.setRemainingQuestions(soup.getDefaultMaxQuestions());
        gameSessionRepository.saveAndFlush(gameSession);

        initializeProgress(gameSessionId, soupId);

        return toStartSoloVO(gameSession, soup, false);
    }

    @Override
    public List<OngoingSoloVO> listOngoing() {
        Long userId = BaseContext.getCurrentId();
        List<GameSession> sessions = gameSessionRepository
                .findByUserIdAndPlayModeAndStatusAndIsDeletedFalseOrderByStartTimeDesc(
                        userId, PlayMode.SOLO, GameSession.GameSessionStatus.ONGOING);
        Set<String> soupIds = sessions.stream()
                .map(GameSession::getSoupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, HaiGuiSoup> soupMap = soupIds.isEmpty()
                ? Map.of()
                : haiGuiSoupRepository.findAllById(soupIds).stream()
                        .collect(Collectors.toMap(HaiGuiSoup::getSoupId, s -> s, (a, b) -> a));
        return sessions.stream()
                .map(session -> toOngoingSoloVO(session, soupMap.get(session.getSoupId())))
                .collect(Collectors.toList());
    }

    @Override
    public RoomGetClueVO getSoloState(String gameSessionId) {
        GameSession session = requireSoloSession(gameSessionId);
        HaiGuiSoup soup = haiGuiSoupRepository.findById(session.getSoupId())
                .orElseThrow(() -> new BusinessException(404, "海龟汤不存在"));

        List<HaiGuiChatMessageWithFragments> messages =
                haiGuiChatMessageRepository.findAllByGameSessionIdOrderByCreatedAtAsc(gameSessionId);

        RoomGetClueVO vo = new RoomGetClueVO();
        vo.setGameSessionId(gameSessionId);
        vo.setSoupSurface(soup.getSoupSurface());
        vo.setProgress(session.getCurrentProgress().doubleValue());
        vo.setRemainingQuestions(session.getRemainingQuestions());
        vo.setQuestion(ChatServicesImpl.getQuestions(messages));

        if (session.getStatus() == GameSession.GameSessionStatus.ONGOING) {
            gameProgressEnricher.enrichInGameProgress(vo, session);
        }

        if (session.getStatus() == GameSession.GameSessionStatus.COMPLETED
                || session.getStatus() == GameSession.GameSessionStatus.CANCELED) {
            vo.setMessage("游戏已结束");
            vo.setSoupBottom(soup.getSoupBottom());
        }
        return vo;
    }

    @Override
    public EndGameVO giveUp(String gameSessionId) {
        requireSoloSession(gameSessionId);
        return soupQuestionService.endSoloGame(gameSessionId, GameEndReason.MANUAL_GIVE_UP);
    }

    @Override
    public EndGameVO getSettlement(String gameSessionId) {
        GameSession session = gameSessionRepository.findById(gameSessionId)
                .orElseThrow(() -> new BusinessException(404, "游戏会话不存在"));
        if (session.getPlayMode() != PlayMode.SOLO) {
            throw new BusinessException(400, "非单人游戏会话");
        }
        if (!Objects.equals(session.getUserId(), BaseContext.getCurrentId())) {
            throw new BusinessException(403, "无权查看此游戏会话");
        }
        if (session.getStatus() == GameSession.GameSessionStatus.ONGOING) {
            throw new BusinessException(400, "游戏尚未结束");
        }
        GameSettlementSnapshot snapshot = gameSettlementBuilder.buildByGameSessionId(gameSessionId);
        HaiGuiSoup soup = haiGuiSoupRepository.findById(session.getSoupId())
                .orElseThrow(() -> new BusinessException(404, "海龟汤不存在"));
        EndGameVO vo = EndGameVO.fromSnapshot(snapshot);
        if (session.getEndReason() != null) {
            vo.setEndReason(session.getEndReason().name());
        }
        ReplayBuildHints replayHints = new ReplayBuildHints();
        replayHints.setSnapshot(snapshot);
        replayHints.setSoupId(soup.getSoupId());
        replayHints.setSoupSurface(soup.getSoupSurface());
        replayHints.setEndTime(session.getEndTime());
        replayHints.setEndReason(session.getEndReason());
        vo.setReplayDetail(gameReplayService.getOrBuildAndCache(
                gameSessionId, null, BaseContext.getCurrentId(), replayHints));
        return vo;
    }

    private GameSession requireSoloSession(String gameSessionId) {
        GameSession session = gameSessionRepository.findById(gameSessionId)
                .orElseThrow(() -> new BusinessException(404, "游戏会话不存在"));
        if (session.getPlayMode() != PlayMode.SOLO) {
            throw new BusinessException(400, "非单人游戏会话");
        }
        if (!Objects.equals(session.getUserId(), BaseContext.getCurrentId())) {
            throw new BusinessException(403, "无权操作此游戏会话");
        }
        return session;
    }

    private void initializeProgress(String gameSessionId, String soupId) {
        List<InferenceTask> tasks = inferenceTaskRepository.findBySoupId(soupId);
        List<HaiGuiGameProgress> progresses = tasks.stream()
                .map(task -> {
                    HaiGuiGameProgress progress = new HaiGuiGameProgress();
                    progress.setGameSessionId(gameSessionId);
                    progress.setTaskId(task.getTaskId());
                    progress.setCompleted(false);
                    progress.setTriggeredFragmentIds(new HashSet<>());
                    progress.setCompletionTime(null);
                    return progress;
                })
                .collect(Collectors.toList());
        haiGuiGameProgressRepository.saveAll(progresses);
    }

    private OngoingSoloVO toOngoingSoloVO(GameSession session, HaiGuiSoup soup) {
        OngoingSoloVO vo = new OngoingSoloVO();
        vo.setGameSessionId(session.getSessionId());
        vo.setSoupId(session.getSoupId());
        vo.setSoupTitle(soup != null ? soup.getSoupTitle() : "单人游玩");
        vo.setSoupSurface(soup != null ? soup.getSoupSurface() : null);
        vo.setRemainingQuestions(session.getRemainingQuestions());
        vo.setProgress(session.getCurrentProgress().doubleValue());
        vo.setStartTime(session.getStartTime());
        return vo;
    }

    private static StartSoloVO toStartSoloVO(GameSession session, HaiGuiSoup soup, boolean resumed) {
        StartSoloVO vo = new StartSoloVO();
        vo.setGameSessionId(session.getSessionId());
        vo.setSoupId(soup.getSoupId());
        vo.setSoupTitle(soup.getSoupTitle());
        vo.setSoupSurface(soup.getSoupSurface());
        vo.setRemainingQuestions(session.getRemainingQuestions());
        vo.setProgress(session.getCurrentProgress().doubleValue());
        vo.setResumed(resumed);
        return vo;
    }
}
