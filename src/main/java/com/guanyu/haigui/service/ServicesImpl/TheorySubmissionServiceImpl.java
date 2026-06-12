package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.GameEndReason;
import com.guanyu.haigui.Enum.PlayMode;
import com.guanyu.haigui.Enum.TheorySubmissionStatus;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.config.TheorySubmissionProperties;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.model.ChatGameMember;
import com.guanyu.haigui.pojo.model.ChatGameMemberId;
import com.guanyu.haigui.pojo.model.GameSession;
import com.guanyu.haigui.pojo.model.HaiGuiTheorySubmission;
import com.guanyu.haigui.pojo.model.InferenceTask;
import com.guanyu.haigui.pojo.vo.EndGameVO;
import com.guanyu.haigui.pojo.vo.SubmitTheoryVO;
import com.guanyu.haigui.pojo.vo.TheoryUnlockVO;
import com.guanyu.haigui.repository.ChatGameMemberRepository;
import com.guanyu.haigui.repository.ChatGameRepository;
import com.guanyu.haigui.repository.GameSessionRepository;
import com.guanyu.haigui.repository.HaiGuiTheorySubmissionRepository;
import com.guanyu.haigui.service.AchievementService;
import com.guanyu.haigui.service.TheorySubmissionService;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.VectorSimilarityUtil;
import com.guanyu.haigui.pojo.vo.BatchEncodeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class TheorySubmissionServiceImpl implements TheorySubmissionService {

    private final GameSessionRepository gameSessionRepository;
    private final ChatGameRepository chatGameRepository;
    private final ChatGameMemberRepository chatGameMemberRepository;
    private final HaiGuiTheorySubmissionRepository haiGuiTheorySubmissionRepository;
    private final GameProgressEnricher gameProgressEnricher;
    private final TheorySubmissionProperties properties;
    private final SoupQuestionServiceImpl soupQuestionService;
    private final AchievementService achievementService;

    @Override
    public SubmitTheoryVO submitTheory(String gameSessionId, String theory) {
        Long userId = BaseContext.getCurrentId();
        if (!StringUtils.hasText(gameSessionId)) {
            throw new BusinessException(400, "游戏会话 ID 不能为空");
        }
        String trimmedTheory = theory != null ? theory.trim() : "";
        if (trimmedTheory.length() < properties.getMinTheoryLength()) {
            throw new BusinessException(400,
                    String.format("推理内容至少 %d 字", properties.getMinTheoryLength()));
        }

        GameSession session = gameSessionRepository.findById(gameSessionId)
                .orElseThrow(() -> new BusinessException(404, "游戏会话不存在"));
        assertCanAccessSession(session, userId);

        if (session.getStatus() != GameSession.GameSessionStatus.ONGOING) {
            throw new BusinessException(400, "游戏已结束");
        }

        GameProgressEnricher.InGameProgressSnapshot snapshot = gameProgressEnricher.snapshot(session);
        TheoryUnlockVO unlock = snapshot.theoryUnlock();

        if (!Boolean.TRUE.equals(unlock.getTheorySubmitEnabled())) {
            return buildResponse(
                    TheorySubmissionStatus.LOCKED,
                    null,
                    List.of(),
                    unlock.getLockReason() != null ? unlock.getLockReason() : "暂不可提交推理",
                    unlock,
                    null);
        }

        achievementService.onTheorySubmitted(userId, gameSessionId);

        CoverageEvaluation evaluation = evaluateCoverage(trimmedTheory, snapshot.completedTasks());
        HaiGuiTheorySubmission record = new HaiGuiTheorySubmission();
        record.setGameSessionId(gameSessionId);
        record.setUserId(userId);
        record.setTheoryText(trimmedTheory);
        record.setCoverageScore(BigDecimal.valueOf(evaluation.coverageScore()).setScale(4, RoundingMode.HALF_UP));

        double progress = GameProgressEnricher.toPercent(session.getCurrentProgress());

        if (evaluation.coverageScore() < properties.getCoverageReject()) {
            record.setVerdict(TheorySubmissionStatus.REJECTED);
            record.setFormalAttempt(false);
            record.setQuestionDeducted(false);
            haiGuiTheorySubmissionRepository.save(record);

            TheoryUnlockVO refreshed = refreshUnlock(session, snapshot.formalAttemptsUsed());
            String message = evaluation.missingTaskNames().isEmpty()
                    ? "推理尚未覆盖已解锁的关键点，请继续提问"
                    : "尚未解释：" + String.join("、", evaluation.missingTaskNames());
            return buildResponse(
                    TheorySubmissionStatus.REJECTED,
                    evaluation.coverageScore(),
                    evaluation.missingTaskNames(),
                    message,
                    refreshed,
                    null);
        }

        record.setFormalAttempt(true);

        if (evaluation.coverageScore() >= properties.getCoverageWin()
                && progress >= properties.getWinProgress()) {
            record.setVerdict(TheorySubmissionStatus.WIN);
            record.setQuestionDeducted(false);
            haiGuiTheorySubmissionRepository.save(record);

            EndGameVO endGame = finishWithGuessCorrect(session);
            TheoryUnlockVO refreshed = refreshUnlock(session, snapshot.formalAttemptsUsed() + 1);
            SubmitTheoryVO vo = buildResponse(
                    TheorySubmissionStatus.WIN,
                    evaluation.coverageScore(),
                    List.of(),
                    "推理正确，恭喜通关！",
                    refreshed,
                    endGame);
            vo.setRemainingFormalAttempts(refreshed.getRemainingFormalAttempts());
            return vo;
        }

        record.setVerdict(TheorySubmissionStatus.PARTIAL);
        record.setQuestionDeducted(true);
        haiGuiTheorySubmissionRepository.save(record);

        if (session.getRemainingQuestions() > 0) {
            session.setRemainingQuestions(session.getRemainingQuestions() - 1);
            gameSessionRepository.save(session);
        }

        TheoryUnlockVO refreshed = refreshUnlock(session, snapshot.formalAttemptsUsed() + 1);
        String message = evaluation.missingTaskNames().isEmpty()
                ? "方向接近，但还不足以宣告通关，请继续完善推理"
                : "部分正确，仍需解释：" + String.join("、", evaluation.missingTaskNames());
        if (session.getRemainingQuestions() <= 0 && session.getPlayMode() == PlayMode.SOLO) {
            EndGameVO endGame = soupQuestionService.endSoloGame(gameSessionId, GameEndReason.QUESTIONS_EXHAUSTED);
            SubmitTheoryVO vo = buildResponse(
                    TheorySubmissionStatus.PARTIAL,
                    evaluation.coverageScore(),
                    evaluation.missingTaskNames(),
                    message + "；提问次数已用尽，本局结束",
                    refreshed,
                    endGame);
            vo.setRemainingFormalAttempts(refreshed.getRemainingFormalAttempts());
            return vo;
        }
        if (session.getRemainingQuestions() <= 0 && session.getPlayMode() == PlayMode.MULTI) {
            String roomId = chatGameRepository.findFirstByGameSessionId(gameSessionId)
                    .map(game -> game.getRoomId())
                    .orElse(null);
            if (roomId != null) {
                EndGameVO endGame = soupQuestionService.endGame(roomId, GameEndReason.QUESTIONS_EXHAUSTED);
                SubmitTheoryVO vo = buildResponse(
                        TheorySubmissionStatus.PARTIAL,
                        evaluation.coverageScore(),
                        evaluation.missingTaskNames(),
                        message + "；提问次数已用尽，本局结束",
                        refreshed,
                        endGame);
                vo.setRemainingFormalAttempts(refreshed.getRemainingFormalAttempts());
                return vo;
            }
        }

        SubmitTheoryVO vo = buildResponse(
                TheorySubmissionStatus.PARTIAL,
                evaluation.coverageScore(),
                evaluation.missingTaskNames(),
                message + "（已消耗 1 次提问机会）",
                refreshed,
                null);
        vo.setRemainingFormalAttempts(refreshed.getRemainingFormalAttempts());
        return vo;
    }

    private EndGameVO finishWithGuessCorrect(GameSession session) {
        if (session.getPlayMode() == PlayMode.SOLO) {
            return soupQuestionService.endSoloGame(session.getSessionId(), GameEndReason.GUESS_CORRECT);
        }
        String roomId = chatGameRepository.findFirstByGameSessionId(session.getSessionId())
                .map(game -> game.getRoomId())
                .orElseThrow(() -> new BusinessException(404, "未找到关联大厅"));
        return soupQuestionService.endGame(roomId, GameEndReason.GUESS_CORRECT);
    }

    private void assertCanAccessSession(GameSession session, Long userId) {
        if (session.getPlayMode() == PlayMode.SOLO) {
            if (!Objects.equals(session.getUserId(), userId)) {
                throw new BusinessException(403, "无权操作此游戏会话");
            }
            return;
        }
        String roomId = chatGameRepository.findFirstByGameSessionId(session.getSessionId())
                .map(game -> game.getRoomId())
                .orElseThrow(() -> new BusinessException(404, "未找到关联大厅"));
        ChatGameMember member = chatGameMemberRepository
                .findById(new ChatGameMemberId(userId, roomId))
                .orElse(null);
        if (member == null) {
            throw new BusinessException(403, "您不是该房间成员");
        }
    }

    private TheoryUnlockVO refreshUnlock(GameSession session, long formalAttemptsUsed) {
        GameProgressEnricher.InGameProgressSnapshot snapshot = gameProgressEnricher.snapshot(session);
        TheoryUnlockVO unlock = snapshot.theoryUnlock();
        unlock.setRemainingFormalAttempts(Math.max(
                0,
                properties.getMaxFormalAttempts() - (int) formalAttemptsUsed));
        if (unlock.getRemainingFormalAttempts() <= 0) {
            unlock.setTheorySubmitEnabled(false);
            unlock.setLockReason("本局正式提交次数已用完");
        }
        return unlock;
    }

    private CoverageEvaluation evaluateCoverage(String theory, List<InferenceTask> completedTasks) {
        if (completedTasks == null || completedTasks.isEmpty()) {
            return new CoverageEvaluation(0.0, List.of("推理任务"));
        }

        List<String> goalTexts = new ArrayList<>();
        for (InferenceTask task : completedTasks) {
            if (StringUtils.hasText(task.getReasoningGoal())) {
                goalTexts.add(task.getReasoningGoal());
            } else if (StringUtils.hasText(task.getTaskDescription())) {
                goalTexts.add(task.getTaskDescription());
            } else {
                goalTexts.add(task.getTaskName());
            }
        }

        List<Float> theoryVector = soupQuestionService.vectorizeQuestion(theory);
        if (theoryVector.isEmpty()) {
            throw new BusinessException(500, "推理向量化失败，请稍后重试");
        }

        List<List<Float>> goalVectors;
        try {
            BatchEncodeResponse batch = BgeVectorClientUtil.encodeBatch(goalTexts);
            goalVectors = batch.getEmbeddings();
        } catch (Exception e) {
            throw new BusinessException(500, "推理评分失败，请稍后重试");
        }
        if (goalVectors == null || goalVectors.size() != goalTexts.size()) {
            throw new BusinessException(500, "推理评分失败，请稍后重试");
        }

        double coveredWeight = 0.0;
        double totalWeight = 0.0;
        List<String> missingNames = new ArrayList<>();

        for (int i = 0; i < completedTasks.size(); i++) {
            InferenceTask task = completedTasks.get(i);
            double weight = task.getProgressWeight() != null
                    ? task.getProgressWeight().doubleValue()
                    : 0.0;
            totalWeight += weight;

            double similarity = VectorSimilarityUtil.cosineSimilarity(theoryVector, goalVectors.get(i));
            if (similarity >= properties.getTaskSimilarityThreshold()) {
                coveredWeight += weight;
            } else {
                missingNames.add(task.getTaskName());
            }
        }

        double coverage = totalWeight > 0 ? coveredWeight / totalWeight : 0.0;
        return new CoverageEvaluation(coverage, missingNames);
    }

    private SubmitTheoryVO buildResponse(
            TheorySubmissionStatus status,
            Double coverageScore,
            List<String> missingTasks,
            String message,
            TheoryUnlockVO unlock,
            EndGameVO endGame) {
        SubmitTheoryVO vo = new SubmitTheoryVO();
        vo.setStatus(status);
        vo.setCoverageScore(coverageScore != null
                ? BigDecimal.valueOf(coverageScore).setScale(2, RoundingMode.HALF_UP).doubleValue()
                : null);
        vo.setMissingTasks(missingTasks == null || missingTasks.isEmpty() ? null : missingTasks);
        vo.setMessage(message);
        vo.setTheoryUnlock(unlock);
        vo.setRemainingFormalAttempts(unlock != null ? unlock.getRemainingFormalAttempts() : null);
        vo.setEndGame(endGame);
        return vo;
    }

    private record CoverageEvaluation(double coverageScore, List<String> missingTaskNames) {
    }
}
