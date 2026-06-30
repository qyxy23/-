package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.*;
import com.guanyu.haigui.Exception.AiResponseException;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.Info.SoupInfo;
import com.guanyu.haigui.pojo.dto.ReplayBuildHints;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.response.AIResponse;
import com.guanyu.haigui.pojo.result.ChatWithAIRoomRequest;
import com.guanyu.haigui.pojo.result.ContextMatchResult;
import com.guanyu.haigui.pojo.result.GameSettlementSnapshot;
import com.guanyu.haigui.pojo.vo.AchievementView;
import com.guanyu.haigui.pojo.vo.EndGameVO;
import com.guanyu.haigui.pojo.vo.MemberContributionView;
import com.guanyu.haigui.pojo.vo.RoomGetClueVO;
import com.guanyu.haigui.pojo.vo.RoomSoupQuestionVO;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.repository.*;
import com.guanyu.haigui.service.AchievementService;
import com.guanyu.haigui.service.GameReplayService;
import com.guanyu.haigui.service.PlayQuotaService;
import com.guanyu.haigui.service.SoupQuestionService;
import com.guanyu.haigui.service.TheoryDraftService;
import com.guanyu.haigui.service.VoteTimeoutService;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import com.guanyu.haigui.utils.SoupMetaQuestionGuide;
import com.guanyu.haigui.utils.SoupMetaQuestionMatcher;
import com.guanyu.haigui.utils.SoupQuestionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 海龟汤问题处理服务实现类
 * 基于向量匹配和AI判断的智能问答系统
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SoupQuestionServiceImpl implements SoupQuestionService {

    private static final int CLUE_RECALL_TOP_K = 8;
    private static final double CLUE_RECALL_MIN_SIMILARITY = 0.35;
    private static final double CLUE_TRIGGER_MIN_SIMILARITY = 0.45;
    private static final int MAX_TRIGGER_PER_QUESTION = 2;
    private static final Pattern TRIGGERED_ID_PATTERN = Pattern.compile("F?(\\d+)");

    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final ClueFragmentRepository clueFragmentRepository;
    private final InferenceTaskRepository inferenceTaskRepository;
    private final RedisStackClient redisClient;
    private final AIManager aiManager;
    private final ChatGameRepository chatGameRepository;
    private final ChatGameMemberRepository chatGameMemberRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final GameSessionRepository gameSessionRepository;
    private final UserInfoRepository userRepository;
    private final HaiGuiChatMessageRepository haiGuiChatMessageRepository;
    private final HaiGuiGameProgressRepository haiGuiGameProgressRepository;
    private final HaiGuiVoteSessionRepository haiGuiVoteSessionRepository;
    private final HaiGuiVoteRecordRepository haiGuiVoteRecordRepository;
    private final GameSettlementBuilder gameSettlementBuilder;
    private final VoteTimeoutService voteTimeoutService;
    private final PlayQuotaService playQuotaService;
    private final GameReplayService gameReplayService;
    private final GameProgressEnricher gameProgressEnricher;
    private final MemberContributionAggregator memberContributionAggregator;
    private final AchievementService achievementService;
    private final TheoryDraftService theoryDraftService;




    @Value("${haiqutang.ai.debug-mode:false}")
    private boolean debugMode;


    @Override
    public RoomSoupQuestionVO RoomProcessSoupQuestion(ChatWithAIRoomRequest request) {
        Long userId = BaseContext.getCurrentId();
        UserInfo user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return RoomSoupQuestionVO.error("用户不存在：ID=" + userId);
        }

        String gameSessionId = request.getGameSessionId();
        if (gameSessionId == null || gameSessionId.isBlank()) {
            return RoomSoupQuestionVO.error("游戏会话 ID 不能为空");
        }

        GameSession session = gameSessionRepository.findById(gameSessionId).orElse(null);
        if (session == null || session.getStatus() != GameSession.GameSessionStatus.ONGOING) {
            return RoomSoupQuestionVO.error("游戏未开始或已结束：gameSessionId=" + gameSessionId);
        }
        if (session.getRemainingQuestions() <= 0) {
            return RoomSoupQuestionVO.error("游戏次数已用完，可充值解锁更多机会");
        }

        String question = request.getQuestion() != null ? request.getQuestion().trim() : "";

        if (session.getPlayMode() == PlayMode.SOLO) {
            return processSoloSoupQuestion(session, userId, gameSessionId, question);
        }

        ChatGame game = chatGameRepository.findFirstByGameSessionId(gameSessionId).orElse(null);
        if (game == null) {
            return RoomSoupQuestionVO.error("未找到关联大厅，gameSessionId=" + gameSessionId);
        }
        String roomId = game.getRoomId();

        if (!(game.getStatus() == RoomStatus.ACTIVE || game.getStatus() == RoomStatus.VOTING)) {
            return RoomSoupQuestionVO.error("游戏未开始或已结束：roomId=" + roomId);
        }

        ChatGameMember member = chatGameMemberRepository
                .findById(new ChatGameMemberId(userId, roomId)).orElse(null);
        if (member == null) {
            return RoomSoupQuestionVO.error("用户未加入房间：ID=" + userId);
        }

        String soupId = game.getHaiGuiSoup().getSoupId();
        HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElse(null);
        if (soup == null) {
            return RoomSoupQuestionVO.error("海龟汤不存在：ID=" + soupId);
        }

        QuestionRunResult result = runQuestionPipeline(session, userId, question);
        if (result.error() != null) {
            return RoomSoupQuestionVO.error(result.error());
        }

        RoomSoupQuestionVO roomSoupQuestionVO = RoomSoupQuestionVO.success(
                roomId,
                gameSessionId,
                question,
                result.displayAnswer(),
                result.totalProgress(),
                result.progressDelta(),
                result.remainingQuestions()
        );
        attachMemberContributions(roomSoupQuestionVO, session);
        simpMessagingTemplate.convertAndSend("/topic/memberChange" + roomId, roomSoupQuestionVO);
        if (result.remainingQuestions() == 0) {
            endGame(roomId, GameEndReason.QUESTIONS_EXHAUSTED);
        }
        return roomSoupQuestionVO;
    }

    private RoomSoupQuestionVO processSoloSoupQuestion(
            GameSession session, Long userId, String gameSessionId, String question) {
        if (!Objects.equals(session.getUserId(), userId)) {
            return RoomSoupQuestionVO.error("无权操作此游戏会话");
        }

        QuestionRunResult result = runQuestionPipeline(session, userId, question);
        if (result.error() != null) {
            return RoomSoupQuestionVO.error(result.error());
        }

        RoomSoupQuestionVO vo = RoomSoupQuestionVO.success(
                null,
                gameSessionId,
                question,
                result.displayAnswer(),
                result.totalProgress(),
                result.progressDelta(),
                result.remainingQuestions()
        );

        if (result.remainingQuestions() == 0) {
            endSoloGame(gameSessionId, GameEndReason.QUESTIONS_EXHAUSTED);
        }
        return vo;
    }

    private record QuestionRunResult(
            String error,
            String displayAnswer,
            double totalProgress,
            double progressDelta,
            int remainingQuestions
    ) {
        static QuestionRunResult fail(String message) {
            return new QuestionRunResult(message, null, 0, 0, 0);
        }

        static QuestionRunResult ok(String displayAnswer, double totalProgress,
                                    double progressDelta, int remainingQuestions) {
            return new QuestionRunResult(null, displayAnswer, totalProgress, progressDelta, remainingQuestions);
        }
    }

    private QuestionRunResult runQuestionPipeline(GameSession session, Long userId, String question) {
        String gameSessionId = session.getSessionId();
        String soupId = session.getSoupId();

        Optional<String> validationError = SoupQuestionValidator.validate(question);
        if (validationError.isPresent()) {
            return QuestionRunResult.fail(validationError.get());
        }

        SoupInfo soupInfo = getSoupInfo(soupId);
        if (soupInfo == null) {
            return QuestionRunResult.fail("海龟汤不存在");
        }

        Optional<QuestionWithAiAnswer> metaAnswer = SoupMetaQuestionMatcher.tryResolve(
                question, soupInfo.getLogicMode(), soupInfo.getContentTone());
        if (metaAnswer.isPresent()) {
            return persistQuestionResult(session, userId, question, metaAnswer.get(), Set.of(), soupId);
        }

        List<Float> questionVector = vectorizeQuestion(question);
        if (questionVector.isEmpty()) {
            return QuestionRunResult.fail("问题向量化失败");
        }

        Map<String, List<ContextMatchResult>> relevantClues = searchRelevantClues(
                soupId, questionVector, CLUE_RECALL_TOP_K, CLUE_RECALL_MIN_SIMILARITY, question);

        Set<Long> triggeredFragmentIds = getCurrentTriggeredFragments(gameSessionId);
        List<InferenceTask> allTasks = inferenceTaskRepository.findBySoupId(soupId);
        List<InferenceTask> incompleteTasks = filterIncompleteTasks(gameSessionId, allTasks);

        List<ContextMatchResult> candidateClues = relevantClues.getOrDefault("CLUE_FRAGMENT", List.of());
        String aiPrompt = buildAIPrompt(question, candidateClues, soupInfo);
        String aiResponse = generateAIResponse(aiPrompt);
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return QuestionRunResult.fail("AI判断生成失败");
        }

        AIResponse parsedResponse = parseAnswer(aiResponse);
        if (parsedResponse == null || parsedResponse.getAnswer() == null) {
            return QuestionRunResult.fail("AI响应解析失败,请联系管理员");
        }

        String answerText = parsedResponse.getAnswer().trim();
        if (isInvalidQuestionAnswer(answerText)) {
            return QuestionRunResult.fail("请用「是不是…」形式的封闭疑问句提问");
        }

        Set<Long> confirmedFragments = confirmTriggeredFragments(
                parsedResponse.getTriggeredFragmentIds(), candidateClues, triggeredFragmentIds);
        QuestionWithAiAnswer answerEnum = mapAnswerToEnum(answerText);
        return persistQuestionResult(session, userId, question, answerEnum, confirmedFragments, soupId);
    }

    /** 扣减提问次数、落库问答记录（元问题快路径与 AI 判题共用） */
    private QuestionRunResult persistQuestionResult(
            GameSession session, Long userId, String question,
            QuestionWithAiAnswer answerEnum, Set<Long> confirmedFragments, String soupId) {
        String gameSessionId = session.getSessionId();
        List<InferenceTask> allTasks = inferenceTaskRepository.findBySoupId(soupId);
        List<InferenceTask> incompleteTasks = filterIncompleteTasks(gameSessionId, allTasks);
        BigDecimal progressDelta = updateGameProgress(gameSessionId, confirmedFragments, incompleteTasks);

        BigDecimal totalProgress = session.getCurrentProgress().add(progressDelta);
        session.updateProgress(totalProgress);
        session.setRemainingQuestions(session.getRemainingQuestions() - 1);
        gameSessionRepository.save(session);

        HaiGuiChatMessageWithFragments message = new HaiGuiChatMessageWithFragments();
        message.setQuestionContent(question);
        message.setAiAnswer(answerEnum);
        message.setGameSessionId(gameSessionId);
        message.setUserId(userId);
        message.setIsDeleted(false);
        message.setTriggeredFragmentIds(new HashSet<>(confirmedFragments));
        haiGuiChatMessageRepository.save(message);
        achievementService.checkAfterQuestion(userId, gameSessionId);

        return QuestionRunResult.ok(
                answerEnum.getDescription(),
                totalProgress.doubleValue(),
                progressDelta.doubleValue(),
                session.getRemainingQuestions()
        );
    }

    public EndGameVO endSoloGame(String gameSessionId, GameEndReason endReason) {
        GameSession gameSession = gameSessionRepository.findById(gameSessionId)
                .orElseThrow(() -> new BusinessException(404, "游戏会话不存在"));
        if (gameSession.getPlayMode() != PlayMode.SOLO) {
            throw new BusinessException(400, "非单人游戏会话");
        }
        if (!Objects.equals(gameSession.getUserId(), BaseContext.getCurrentId())) {
            throw new BusinessException(403, "无权操作此游戏会话");
        }
        if (gameSession.getStatus() != GameSession.GameSessionStatus.ONGOING) {
            throw new BusinessException(400, "游戏已结束");
        }

        GameSettlementSnapshot snapshot = gameSettlementBuilder.buildByGameSessionId(gameSessionId);

        if (endReason == GameEndReason.MANUAL_GIVE_UP) {
            gameSession.setStatus(GameSession.GameSessionStatus.CANCELED);
        } else {
            gameSession.setStatus(GameSession.GameSessionStatus.COMPLETED);
        }
        gameSession.setEndReason(endReason);
        gameSession.setEndTime(LocalDateTime.now());
        gameSession.setCurrentProgress(snapshot.getProgressPercent());
        gameSession.setScore(BigDecimal.valueOf(resolveFinalScore(snapshot, endReason)));
        gameSessionRepository.save(gameSession);

        HaiGuiSoup soup = haiGuiSoupRepository.findById(gameSession.getSoupId())
                .orElseThrow(() -> new BusinessException(404, "该海龟汤不存在"));
        soup.setPlayCount(soup.getPlayCount() + 1);
        haiGuiSoupRepository.save(soup);

        playQuotaService.chargeOnSettlement(gameSessionId);

        EndGameVO endGameVO = EndGameVO.fromSnapshot(snapshot);
        endGameVO.setEndReason(endReason.name());
        ReplayBuildHints replayHints = new ReplayBuildHints();
        replayHints.setSnapshot(snapshot);
        replayHints.setSoupId(soup.getSoupId());
        replayHints.setSoupSurface(soup.getSoupSurface());
        replayHints.setEndTime(gameSession.getEndTime());
        replayHints.setEndReason(endReason);
        gameReplayService.attachReplayAtEnd(
                endGameVO, gameSessionId, null, gameSession.getUserId(), replayHints);
        if (endReason == GameEndReason.MANUAL_GIVE_UP) {
            List<AchievementView> giveUpUnlocked =
                    achievementService.onManualGiveUp(gameSession.getUserId(), gameSessionId);
            endGameVO.setNewlyUnlockedAchievements(giveUpUnlocked);
        } else {
            applySettlementAchievements(gameSession, null, snapshot, endGameVO);
        }
        return endGameVO;
    }

    @Override
    public RoomGetClueVO getClue(String roomId) {
        ChatGame game = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(404, "房间不存在"));
        if (game.getGameSessionId() == null) {
            return RoomGetClueVO.error("游戏未开始", game.getStatus());
        }
        List<HaiGuiChatMessageWithFragments> messages =
                haiGuiChatMessageRepository.findAllByGameSessionId(game.getGameSessionId());
        GameSession session = gameSessionRepository.findById(game.getGameSessionId()).orElse(null);
        if (session == null) {
            return RoomGetClueVO.error("游戏会话不存在",game.getStatus());
        }
        HaiGuiSoup soup = haiGuiSoupRepository.findById(session.getSoupId()).orElse(null);
        if (soup == null) {
            return RoomGetClueVO.error("海龟汤不存在",game.getStatus());
        }
        RoomGetClueVO roomGetClueVO = new RoomGetClueVO();
        roomGetClueVO.setGameSessionId(game.getGameSessionId());
        roomGetClueVO.setSoupSurface(soup.getSoupSurface());
        if(game.getStatus()==RoomStatus.FINISHED){
            roomGetClueVO.setMessage("游戏已结束");
            roomGetClueVO.setSoupBottom(soup.getSoupBottom());
        }else if(game.getStatus()==RoomStatus.VOTING){
            List<HaiGuiVoteSession> sessions = haiGuiVoteSessionRepository.findByGameSessionIdAndStatusOrderByCreatedAtDesc(
                    game.getGameSessionId(), HaiGuiVoteSession.VoteStatus.ONGOING);
            HaiGuiVoteSession currentSession = sessions.get(0); // 最新创建的会话

            // 3. 检查投票是否超时
            if (LocalDateTime.now().isAfter(currentSession.getEndTime())) {
                voteTimeoutService.expireVoteIfOverdue(game, currentSession, VoteTimeoutService.NotifyPolicy.PASSIVE_QUERY);
            } else {
                roomGetClueVO.setAgreedVotes(currentSession.getAgreedVotes());
                roomGetClueVO.setTotalVoters(currentSession.getTotalVoters());
                roomGetClueVO.setEndTime(currentSession.getEndTime());
                roomGetClueVO.setVoteType(currentSession.getVoteType());
                if (currentSession.getVoteType() == VoteType.THEORY_SUBMIT) {
                    roomGetClueVO.setTheoryVotePreview(truncateTheoryPreview(currentSession.getTheoryText()));
                    roomGetClueVO.setTheoryVoteDraftVersion(currentSession.getDraftVersion());
                }
                HaiGuiVoteRecord voteRecord = haiGuiVoteRecordRepository.findByVoteSessionIdAndUserId(
                        currentSession.getVoteSessionId(), BaseContext.getCurrentId());
                roomGetClueVO.setHasVoted(voteRecord != null);
                roomGetClueVO.setAgreed(voteRecord != null && voteRecord.getVoteOption() == HaiGuiVoteRecord.VoteOption.AGREE);
            }
        }
        roomGetClueVO.setRoomStatus(game.getStatus());
        if(game.getStatus()==RoomStatus.WAITING){
            return roomGetClueVO;
        }
        roomGetClueVO.setProgress(session.getCurrentProgress().doubleValue());
        roomGetClueVO.setRemainingQuestions(session.getRemainingQuestions());
        roomGetClueVO.setQuestion(ChatServicesImpl.getQuestions(messages));
        if (game.getStatus() == RoomStatus.ACTIVE || game.getStatus() == RoomStatus.VOTING) {
            gameProgressEnricher.enrichInGameProgress(roomGetClueVO, session);
            Long userId = BaseContext.getCurrentId();
            if (userId != null) {
                roomGetClueVO.setTheoryDraft(theoryDraftService.getDraftStateBySession(
                        game.getGameSessionId(),
                        userId,
                        game.getStatus() == RoomStatus.VOTING));
            }
        }

        return roomGetClueVO;
    }

    private static String truncateTheoryPreview(String theory) {
        if (theory == null) {
            return null;
        }
        String trimmed = theory.trim();
        if (trimmed.length() <= 120) {
            return trimmed;
        }
        return trimmed.substring(0, 120) + "…";
    }

    public EndGameVO endGame(String roomId) {
        return endGame(roomId, GameEndReason.QUESTIONS_EXHAUSTED);
    }

    // 结束游戏
    public EndGameVO endGame(String roomId, GameEndReason endReason) {
        ChatGame chatGame = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(404, "房间不存在"));
        GameSession gameSession = gameSessionRepository.findById(chatGame.getGameSessionId())
                .orElseThrow(() -> new BusinessException(404, "游戏会话不存在"));
        HaiGuiSoup soup = haiGuiSoupRepository.findById(chatGame.getHaiGuiSoup().getSoupId())
                .orElseThrow(() -> new BusinessException(404, "该海龟汤不存在"));

        GameSettlementSnapshot snapshot = gameSettlementBuilder.build(roomId);

        gameSession.setStatus(GameSession.GameSessionStatus.COMPLETED);
        gameSession.setEndReason(endReason);
        gameSession.setEndTime(LocalDateTime.now());
        gameSession.setCurrentProgress(snapshot.getProgressPercent());
        gameSession.setScore(BigDecimal.valueOf(resolveFinalScore(snapshot, endReason)));
        gameSessionRepository.save(gameSession);

        soup.setPlayCount(soup.getPlayCount() + 1);
        haiGuiSoupRepository.save(soup);

        chatGame.setStatus(RoomStatus.FINISHED);
        chatGame.setEndTime(LocalDateTime.now());
        chatGameRepository.save(chatGame);

        List<ChatGameMember> chatGameMember = chatGameMemberRepository.findByIdRoomId(roomId);
        chatGameMember.forEach(member -> member.setStatus(MemberStatus.ONLINE));
        chatGameMemberRepository.saveAll(chatGameMember);

        playQuotaService.chargeOnSettlement(chatGame.getGameSessionId());

        EndGameVO endGameVO = EndGameVO.fromSnapshot(snapshot);
        endGameVO.setEndReason(endReason.name());
        ReplayBuildHints replayHints = new ReplayBuildHints();
        replayHints.setSnapshot(snapshot);
        replayHints.setSoupId(soup.getSoupId());
        replayHints.setSoupSurface(soup.getSoupSurface());
        replayHints.setEndTime(chatGame.getEndTime());
        replayHints.setEndReason(endReason);
        gameReplayService.attachReplayAtEnd(
                endGameVO, chatGame.getGameSessionId(), roomId, null, replayHints);
        applySettlementAchievements(gameSession, roomId, snapshot, endGameVO);
        simpMessagingTemplate.convertAndSend("/topic/memberChange" + roomId, endGameVO);
        return endGameVO;
    }


    protected BigDecimal updateGameProgress(String gameSessionId, Set<Long> newFragments,
                                          List<InferenceTask> incompleteTasks) {
        if (newFragments == null || newFragments.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map<Long, Set<Long>> taskFragmentsMap = getCurrentTaskFragments(gameSessionId, incompleteTasks);
        BigDecimal progress = BigDecimal.ZERO;

        for (InferenceTask task : incompleteTasks) {
            Long taskId = task.getTaskId();
            List<Long> prerequisiteIds = task.getPrerequisiteFragmentIds();

            Set<Long> currentFragments = taskFragmentsMap.getOrDefault(taskId, new HashSet<>());
            Set<Long> updatedFragments = new HashSet<>(currentFragments);

            Set<Long> relevantNewFragments = new HashSet<>(newFragments);
            relevantNewFragments.retainAll(prerequisiteIds);
            updatedFragments.addAll(relevantNewFragments);

            boolean allPrerequisitesMet = !prerequisiteIds.isEmpty()
                    && updatedFragments.containsAll(prerequisiteIds);

            if (allPrerequisitesMet) {
                log.info("任务完成：ID={}，前置线索={}，已触发线索={}",
                        taskId, prerequisiteIds, updatedFragments);
                progress = progress.add(task.getProgressWeight());
                haiGuiGameProgressRepository.updateTaskStatus(
                        gameSessionId, taskId, true, updatedFragments, LocalDateTime.now());
            } else if (!relevantNewFragments.isEmpty()) {
                log.info("更新任务线索：ID={}，新增线索={}，总线索={}",
                        taskId, relevantNewFragments, updatedFragments);
                haiGuiGameProgressRepository.updateTaskFragments(
                        gameSessionId, taskId, updatedFragments, LocalDateTime.now());
            }
        }

        return progress;
    }

    private Set<Long> confirmTriggeredFragments(Set<Long> aiTriggered,
                                                List<ContextMatchResult> candidates,
                                                Set<Long> alreadyTriggered) {
        if (aiTriggered == null || aiTriggered.isEmpty() || candidates == null || candidates.isEmpty()) {
            return Collections.emptySet();
        }

        Map<Long, Double> scoreById = new HashMap<>();
        for (ContextMatchResult candidate : candidates) {
            try {
                scoreById.put(Long.parseLong(candidate.getId()), candidate.getSimilarity());
            } catch (NumberFormatException e) {
                log.warn("无法解析候选线索ID: {}", candidate.getId());
            }
        }

        Set<Long> confirmed = new LinkedHashSet<>();
        for (Long fragmentId : aiTriggered) {
            if (confirmed.size() >= MAX_TRIGGER_PER_QUESTION) {
                break;
            }
            if (fragmentId == null || alreadyTriggered.contains(fragmentId)) {
                continue;
            }
            Double score = scoreById.get(fragmentId);
            if (score == null) {
                log.warn("AI 触发线索 F{} 不在候选集内，已忽略", fragmentId);
                continue;
            }
            if (score < CLUE_TRIGGER_MIN_SIMILARITY) {
                log.warn("AI 触发线索 F{} 相似度 {} 低于阈值 {}，已忽略",
                        fragmentId, score, CLUE_TRIGGER_MIN_SIMILARITY);
                continue;
            }
            confirmed.add(fragmentId);
        }
        return confirmed;
    }

    private boolean isInvalidQuestionAnswer(String answerText) {
        return "无效提问".equals(answerText) || "INVALID".equalsIgnoreCase(answerText);
    }

    private QuestionWithAiAnswer mapAnswerToEnum(String answerText) {
        if ("是".equals(answerText) || "yes".equalsIgnoreCase(answerText)) {
            return QuestionWithAiAnswer.YES;
        }
        if ("不是".equals(answerText) || "no".equalsIgnoreCase(answerText)) {
            return QuestionWithAiAnswer.NO;
        }
        if ("是或不是".equals(answerText) || "部分正确".equals(answerText) || "无法确定".equals(answerText)) {
            return QuestionWithAiAnswer.PARTIAL;
        }
        if ("不重要".equals(answerText)) {
            return QuestionWithAiAnswer.UNIMPORTANT;
        }
        try {
            return QuestionWithAiAnswer.valueOf(answerText.toUpperCase());
        } catch (IllegalArgumentException e) {
            return QuestionWithAiAnswer.UNKNOWN;
        }
    }

    private Set<Long> parseTriggeredIds(String raw) {
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim()) || "无".equals(raw.trim())) {
            return new LinkedHashSet<>();
        }
        Set<Long> ids = new LinkedHashSet<>();
        Matcher matcher = TRIGGERED_ID_PATTERN.matcher(raw);
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        return ids;
    }

    public String buildAIPrompt(String question,
                                List<ContextMatchResult> candidateClues,
                                SoupInfo soupInfo) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("=== 海龟汤背景 ===\n");
        prompt.append(String.format("汤面：%s\n", soupInfo.getSoupSurface()));
        prompt.append(String.format("汤底：%s\n", soupInfo.getSoupBottom()));
        if (soupInfo.getAiJudgeRules() != null && !soupInfo.getAiJudgeRules().isBlank()) {
            prompt.append(String.format("判题规则：%s\n", soupInfo.getAiJudgeRules()));
        }
        prompt.append("\n");

        if (candidateClues != null && !candidateClues.isEmpty()) {
            prompt.append("=== 候选线索（仅可从下列 ID 中选择是否触发，每次最多 2 条） ===\n");
            for (ContextMatchResult clue : candidateClues) {
                prompt.append(String.format("[F%s] (相似度%.2f) %s\n",
                        clue.getId(), clue.getSimilarity(), clue.getContent()));
            }
            prompt.append("\n");
        } else {
            prompt.append("=== 候选线索 ===\n");
            prompt.append("（无向量召回候选，TRIGGERED 必须为空）\n\n");
        }

        prompt.append("=== 玩家问题 ===\n");
        prompt.append(String.format("%s\n\n", question));

        prompt.append("=== 任务说明 ===\n");
        prompt.append("1. 按判题规则回答玩家封闭问题。\n");
        prompt.append("2. 判断玩家是否真正问到了某条候选线索的核心信息；仅语义明确相关才触发。\n");
        prompt.append("3. 向量误匹配、无关、吐槽、非疑问句 → TRIGGERED 留空。\n");
        prompt.append("4. 问旅行地点/方式等规则规定「不重要」的问题 → ANSWER 答「不重要」，TRIGGERED 通常留空。\n");
        prompt.append("5. TRIGGERED 只能填写候选中的 [F编号]，不得编造 ID。\n");
        prompt.append(SoupMetaQuestionGuide.userPromptTaskSection());
        prompt.append("\n");

        prompt.append("=== 回答要求 ===\n");
        prompt.append("请严格按以下格式返回：\n");
        prompt.append("ANSWER: [是/不是/是或不是/不重要/无效提问]\n");
        prompt.append("REASON: [简短判断理由]\n");
        prompt.append("TRIGGERED: [] 或 [F2] 或 [F2,F7]\n");

        return prompt.toString();
    }

    private List<InferenceTask> filterIncompleteTasks(String gameSessionId, List<InferenceTask> allTasks) {
        if (allTasks == null || allTasks.isEmpty()) {
            return List.of();
        }
        Set<Long> completedTaskIds = haiGuiGameProgressRepository.findByGameSessionId(gameSessionId).stream()
                .filter(HaiGuiGameProgress::getCompleted)
                .map(HaiGuiGameProgress::getTaskId)
                .collect(Collectors.toSet());
        return allTasks.stream()
                .filter(task -> !completedTaskIds.contains(task.getTaskId()))
                .collect(Collectors.toList());
    }

    private Map<Long, Set<Long>> getCurrentTaskFragments(String gameSessionId, List<InferenceTask> tasks) {
        List<Long> taskIds = tasks.stream()
                .map(InferenceTask::getTaskId)
                .collect(Collectors.toList());

        Map<Long, Set<Long>> taskFragmentsMap = new HashMap<>();

        if (!taskIds.isEmpty()) {
            // 调用修改后的仓库方法
            List<Object[]> results = haiGuiGameProgressRepository.findTaskFragmentsRaw(gameSessionId, taskIds);


            for (Object[] result : results) {
                Long taskId = (Long) result[0];
                Object fragmentObj = result[1];

                // 安全地将 Object 转换为 Set<Long>
                Set<Long> fragments;
                if (fragmentObj instanceof Set<?>) {
                    try {
                        fragments = ((Set<?>) fragmentObj).stream()
                                .map(Long.class::cast)
                                .collect(Collectors.toSet());
                    } catch (ClassCastException e) {
                        log.warn("转换任务线索ID失败，taskId={}", taskId, e);
                        fragments = new HashSet<>();
                    }
                } else {
                    fragments = new HashSet<>();
                }

                taskFragmentsMap.put(taskId, fragments);
            }

        }

        // 为所有任务添加条目（即使没有记录）
        for (InferenceTask task : tasks) {
            taskFragmentsMap.putIfAbsent(task.getTaskId(), new HashSet<>());
        }

        return taskFragmentsMap;
    }

    // 辅助方法：获取当前已触发的线索
    private Set<Long> getCurrentTriggeredFragments(String gameSessionId) {
        List<HaiGuiGameProgress> allProgress = haiGuiGameProgressRepository.findByGameSessionId(gameSessionId);
        Set<Long> allFragments = new HashSet<>();
        for (HaiGuiGameProgress progress : allProgress) {
            allFragments.addAll(progress.getTriggeredFragmentIds());
        }
        return allFragments;
    }




    @Override
    public List<Float> vectorizeQuestion(String question) {
        try {
            log.debug("开始向量化问题: {}", question.substring(0, Math.min(30, question.length())));

            // 使用BGE模型向量化
            SingleEncodeResponse response = BgeVectorClientUtil.encodeSingle(question);
            if (response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
                log.error("BGE向量化失败: {}", question);
                return Collections.emptyList();
            }

            List<Float> vector = response.getEmbeddings().get(0);
            log.debug("问题向量化成功，维度: {}", vector.size());

            return vector;

        } catch (Exception e) {
            log.error("向量化问题失败: {}", question, e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, List<ContextMatchResult>> searchRelevantClues(String soupId,
                                                                                   List<Float> questionVector,
                                                                                   int topK,
                                                                                   double minSimilarity,
                                                                                   String question) {
        try {
            log.info("开始搜索相关线索: soupId={}, topK={}, minSimilarity={}, question={}",
                    soupId, topK, minSimilarity, question.substring(0, Math.min(30, question.length())));

            // 使用Redis在指定海龟汤中搜索相似片段（参考searchCluesInSoup的逻辑）
            Map<String, Double> fragmentResults = redisClient.searchSimilarCluesInSoup(
                    questionVector, soupId, topK * 2); // 搜索更多，后续过滤

            // 获取片段详细信息并转换为ContextMatchResult
            Map<String, List<ContextMatchResult>> results = new HashMap<>();
            List<ContextMatchResult> fragmentMatchResults = new ArrayList<>();
            List<Long> candidateFragmentIds = new ArrayList<>();
            Map<String, Double> candidateSimilarities = new HashMap<>();

            for (Map.Entry<String, Double> entry : fragmentResults.entrySet()) {
                String fragmentId = entry.getKey();
                Double similarity = entry.getValue();

                // 过滤低于阈值的匹配结果
                if (similarity < minSimilarity) {
                    continue;
                }

                try {
                    candidateFragmentIds.add(Long.parseLong(fragmentId));
                    candidateSimilarities.put(fragmentId, similarity);
                } catch (NumberFormatException e) {
                    log.warn("无法解析片段ID: {}", fragmentId);
                }
            }

            if (!candidateFragmentIds.isEmpty()) {
                Map<Long, ClueFragment> fragmentMap = clueFragmentRepository
                        .findByFragmentIdInAndIsDeletedFalse(candidateFragmentIds).stream()
                        .collect(Collectors.toMap(ClueFragment::getFragmentId, f -> f, (a, b) -> a));

                for (Long fragId : candidateFragmentIds) {
                    ClueFragment fragment = fragmentMap.get(fragId);
                    if (fragment == null) {
                        continue;
                    }
                    Double similarity = candidateSimilarities.get(String.valueOf(fragId));
                    if (similarity == null) {
                        continue;
                    }
                    fragmentMatchResults.add(new ContextMatchResult(
                            String.valueOf(fragId),
                            fragment.getFragmentContent(),
                            similarity,
                            VectorType.CLUE
                    ));
                }
            }

            // 按相似度排序并限制数量
            fragmentMatchResults.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
            if (fragmentMatchResults.size() > topK) {
                fragmentMatchResults = fragmentMatchResults.subList(0, topK);
            }

            // 如果找到匹配的fragment，添加到结果中
            if (!fragmentMatchResults.isEmpty()) {
                results.put("CLUE_FRAGMENT", fragmentMatchResults);
            }

            int totalMatches = fragmentMatchResults.size();
            log.info("线索搜索完成: 总匹配数={}", totalMatches);

            return results;

        } catch (Exception e) {
            log.error("搜索相关线索失败: soupId={}", soupId, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 获取模拟AI响应数据（调试模式使用）
     * @return 模拟的AI响应JSON字符串
     */
    private String getMockAiResponse() {
        try {
            ClassPathResource resource = new ClassPathResource("temp/aiResponse2.txt");
            if (!resource.exists()) {
                log.error("模拟AI响应文件不存在: temp/aiResponse.txt");
                throw new AiResponseException("模拟AI响应文件不存在");
            }

            byte[] data = FileCopyUtils.copyToByteArray(resource.getInputStream());
            String mockResponse = new String(data, StandardCharsets.UTF_8);

            log.info("成功读取模拟AI响应，文件大小: {} 字节", mockResponse.length());
            return mockResponse;

        } catch (IOException e) {
            log.error("读取模拟AI响应文件失败", e);
            throw new AiResponseException("模拟AI响应文件不存在");
        }
    }

    @Override
    public String generateAIResponse(String prompt) {
        if (debugMode) {
            return getMockAiResponse();
        }
        try {
            log.debug("调用AI生成判断，提示词长度: {}", prompt.length());

            // 更新系统提示词（强调新规则）
            String systemPrompt = "你是海龟汤游戏的AI主持人，根据汤底和判题规则回答玩家的封闭问题，"
                    + "并裁决是否触发候选线索。"
                    + "判题只能回答「是」「不是」「是或不是」「不重要」「无效提问」之一；"
                    + "触发线索只能从候选 [F编号] 中选择，无关问题必须 TRIGGERED: []。"
                    + SoupMetaQuestionGuide.systemPromptSection();

            String response = aiManager.doChat(systemPrompt, prompt);

            if (response == null || response.trim().isEmpty()) {
                log.error("AI返回空响应");
                return null;
            }

            return response;
        } catch (Exception e) {
            log.error("调用AI生成判断失败", e);
            return null;
        }
    }

    @Override
    public AIResponse parseAnswer(String aiResponse) {
        try {
            log.debug("解析AI回答: {}", aiResponse.substring(0, Math.min(100, aiResponse.length())));

            AIResponse response = new AIResponse();
            response.setRawResponse(aiResponse);

            for (String line : aiResponse.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("ANSWER:")) {
                    response.setAnswer(trimmed.replace("ANSWER:", "").trim());
                } else if (trimmed.startsWith("REASON:")) {
                    response.setReason(trimmed.replace("REASON:", "").trim());
                } else if (trimmed.startsWith("TRIGGERED:")) {
                    response.setTriggeredFragmentIds(parseTriggeredIds(trimmed.replace("TRIGGERED:", "").trim()));
                }
            }

            if (response.getAnswer() == null) {
                log.warn("AI响应缺少 ANSWER 字段: {}", aiResponse);
                return fallbackParse(aiResponse);
            }

            return response;
        } catch (Exception e) {
            log.error("解析AI回答失败", e);
            return fallbackParse(aiResponse);
        }
    }

    private AIResponse fallbackParse(String aiResponse) {
        AIResponse response = new AIResponse();
        response.setRawResponse(aiResponse);

        if (aiResponse.contains("ANSWER:")) {
            String answerLine = aiResponse.lines()
                    .filter(l -> l.startsWith("ANSWER:"))
                    .findFirst().orElse("");
            response.setAnswer(answerLine.replace("ANSWER:", "").trim());
        }

        return response;
    }

    @Override
    public SoupInfo getSoupInfo(String soupId) {
        try {
            HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElse(null);
            if (soup == null) {
                log.warn("海龟汤不存在: {}", soupId);
                return null;
            }

            // 可以在这里添加进度计算逻辑
            // soupInfo.setCurrentProgress(calculateProgress(soupId));

            return new SoupInfo(
                    soup.getSoupId(),
                    soup.getSoupTitle(),
                    soup.getSoupSurface(),
                    soup.getSoupBottom(),
                    soup.getAiJudgeRules(),
                    soup.getLogicMode(),
                    soup.getContentTone(),
                    null
            );

        } catch (Exception e) {
            log.error("获取海龟汤信息失败: soupId={}", soupId, e);
            return null;
        }
    }

    private static int resolveFinalScore(GameSettlementSnapshot snapshot, GameEndReason endReason) {
        int score = snapshot.getFinalScore();
        if (endReason == GameEndReason.GUESS_CORRECT) {
            return Math.min(100, Math.max(score, 85) + 5);
        }
        return score;
    }

    private void attachMemberContributions(RoomSoupQuestionVO vo, GameSession session) {
        if (vo == null || session == null || session.getPlayMode() != PlayMode.MULTI) {
            return;
        }
        List<MemberContributionView> contributions = memberContributionAggregator.buildForGameSession(session);
        if (!contributions.isEmpty()) {
            vo.setMemberContributions(contributions);
        }
    }

    private void applySettlementAchievements(GameSession gameSession, String roomId,
                                             GameSettlementSnapshot snapshot, EndGameVO endGameVO) {
        Long currentUserId = BaseContext.getCurrentId();
        List<AchievementView> forCurrent = new ArrayList<>();
        for (Long userId : resolveParticipantUserIds(gameSession, roomId)) {
            List<AchievementView> unlocked =
                    achievementService.evaluateSettlement(userId, gameSession, roomId, snapshot);
            if (Objects.equals(userId, currentUserId)) {
                forCurrent.addAll(unlocked);
            }
        }
        endGameVO.setNewlyUnlockedAchievements(forCurrent);
    }

    private List<Long> resolveParticipantUserIds(GameSession session, String roomId) {
        if (session.getPlayMode() == PlayMode.SOLO) {
            return List.of(session.getUserId());
        }
        if (roomId == null) {
            return List.of();
        }
        return chatGameMemberRepository.findByIdRoomId(roomId).stream()
                .map(member -> member.getMember() != null ? member.getMember().getUserId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

}