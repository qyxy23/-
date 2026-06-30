package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.GameEndReason;
import com.guanyu.haigui.Enum.PlayMode;
import com.guanyu.haigui.Enum.TheoryPartialReason;
import com.guanyu.haigui.Enum.TheorySubmissionStatus;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.config.TheorySubmissionProperties;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.model.ChatGameMember;
import com.guanyu.haigui.pojo.model.ChatGameMemberId;
import com.guanyu.haigui.pojo.model.GameSession;
import com.guanyu.haigui.pojo.model.HaiGuiTheorySubmission;
import com.guanyu.haigui.pojo.model.InferenceTask;
import com.guanyu.haigui.pojo.vo.BatchEncodeResponse;
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
import com.guanyu.haigui.utils.TheorySubmissionHintBuilder;
import com.guanyu.haigui.utils.VectorSimilarityUtil;
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
        return submitTheoryInternal(gameSessionId, theory, false);
    }

    @Override
    public SubmitTheoryVO submitTheoryAfterTeamVote(String gameSessionId, String theory, Long submitterUserId) {
        return submitTheoryInternal(gameSessionId, theory, true, submitterUserId);
    }

    private SubmitTheoryVO submitTheoryInternal(String gameSessionId, String theory, boolean teamVoteApproved) {
        return submitTheoryInternal(gameSessionId, theory, teamVoteApproved, BaseContext.getCurrentId());
    }

    private SubmitTheoryVO submitTheoryInternal(
            String gameSessionId, String theory, boolean teamVoteApproved, Long userId) {
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
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (!teamVoteApproved) {
            assertCanAccessSession(session, userId);
        } else {
            assertMemberForTeamVote(session, userId);
        }

        if (session.getPlayMode() == PlayMode.MULTI && !teamVoteApproved) {
            throw new BusinessException(400, "多人模式请先编辑草案并经全队投票后再提交推理");
        }

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
                    null,
                    null,
                    null,
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
                    null,
                    TheoryPartialReason.COVERAGE_GAP,
                    null,
                    message,
                    refreshed,
                    null);
        }

        if (evaluation.coverageScore() >= properties.getCoverageWin()
                && progress >= properties.getWinProgress()) {
            record.setFormalAttempt(true);
            record.setVerdict(TheorySubmissionStatus.WIN);
            record.setQuestionDeducted(false);
            haiGuiTheorySubmissionRepository.save(record);

            EndGameVO endGame = finishWithGuessCorrect(session);
            TheoryUnlockVO refreshed = refreshUnlock(session, snapshot.formalAttemptsUsed() + 1);
            SubmitTheoryVO vo = buildResponse(
                    TheorySubmissionStatus.WIN,
                    evaluation.coverageScore(),
                    List.of(),
                    null,
                    null,
                    null,
                    "推理正确，恭喜通关！",
                    refreshed,
                    endGame);
            vo.setRemainingFormalAttempts(refreshed.getRemainingFormalAttempts());
            return vo;
        }

        if (isProgressGuidanceZone(progress, evaluation.coverageScore())) {
            return saveProgressGuidance(session, snapshot, record, evaluation, progress);
        }

        return saveFormalPartial(session, snapshot, record, evaluation, progress);
    }

    /** 60%～80% 且 coverage 已够：指引型提交，不扣问次、不占正式配额 */
    private boolean isProgressGuidanceZone(double progress, double coverageScore) {
        return progress >= properties.getUnlockProgress()
                && progress < properties.getWinProgress()
                && coverageScore >= properties.getCoverageWin();
    }

    private SubmitTheoryVO saveProgressGuidance(
            GameSession session,
            GameProgressEnricher.InGameProgressSnapshot snapshot,
            HaiGuiTheorySubmission record,
            CoverageEvaluation evaluation,
            double progress) {
        record.setVerdict(TheorySubmissionStatus.PARTIAL);
        record.setFormalAttempt(false);
        record.setQuestionDeducted(false);
        haiGuiTheorySubmissionRepository.save(record);

        double progressGap = roundGap(properties.getWinProgress() - progress);
        List<String> hints = TheorySubmissionHintBuilder.buildFromIncompleteTasks(
                snapshot.incompleteTasks(), properties.getMaxProgressHints());

        String message = buildProgressGuidanceMessage(progress, progressGap, hints);
        TheoryUnlockVO refreshed = refreshUnlock(session, snapshot.formalAttemptsUsed());

        return buildResponse(
                TheorySubmissionStatus.PARTIAL,
                evaluation.coverageScore(),
                null,
                hints.isEmpty() ? null : hints,
                TheoryPartialReason.PROGRESS_GAP,
                progressGap,
                message,
                refreshed,
                null);
    }

    private SubmitTheoryVO saveFormalPartial(
            GameSession session,
            GameProgressEnricher.InGameProgressSnapshot snapshot,
            HaiGuiTheorySubmission record,
            CoverageEvaluation evaluation,
            double progress) {
        record.setFormalAttempt(true);
        record.setVerdict(TheorySubmissionStatus.PARTIAL);
        record.setQuestionDeducted(true);
        haiGuiTheorySubmissionRepository.save(record);

        if (session.getRemainingQuestions() > 0) {
            session.setRemainingQuestions(session.getRemainingQuestions() - 1);
            gameSessionRepository.save(session);
        }

        TheoryUnlockVO refreshed = refreshUnlock(session, snapshot.formalAttemptsUsed() + 1);
        TheoryPartialReason reason = progress < properties.getWinProgress()
                ? TheoryPartialReason.PROGRESS_GAP
                : TheoryPartialReason.COVERAGE_GAP;

        String message = buildFormalPartialMessage(evaluation, progress, reason);

        if (session.getRemainingQuestions() <= 0 && session.getPlayMode() == PlayMode.SOLO) {
            EndGameVO endGame = soupQuestionService.endSoloGame(
                    session.getSessionId(), GameEndReason.QUESTIONS_EXHAUSTED);
            SubmitTheoryVO vo = buildResponse(
                    TheorySubmissionStatus.PARTIAL,
                    evaluation.coverageScore(),
                    evaluation.missingTaskNames(),
                    null,
                    reason,
                    progress < properties.getWinProgress()
                            ? roundGap(properties.getWinProgress() - progress) : null,
                    message + "；提问次数已用尽，本局结束",
                    refreshed,
                    endGame);
            vo.setRemainingFormalAttempts(refreshed.getRemainingFormalAttempts());
            return vo;
        }
        if (session.getRemainingQuestions() <= 0 && session.getPlayMode() == PlayMode.MULTI) {
            String roomId = chatGameRepository.findFirstByGameSessionId(session.getSessionId())
                    .map(game -> game.getRoomId())
                    .orElse(null);
            if (roomId != null) {
                EndGameVO endGame = soupQuestionService.endGame(roomId, GameEndReason.QUESTIONS_EXHAUSTED);
                SubmitTheoryVO vo = buildResponse(
                        TheorySubmissionStatus.PARTIAL,
                        evaluation.coverageScore(),
                        evaluation.missingTaskNames(),
                        null,
                        reason,
                        progress < properties.getWinProgress()
                                ? roundGap(properties.getWinProgress() - progress) : null,
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
                null,
                reason,
                progress < properties.getWinProgress()
                        ? roundGap(properties.getWinProgress() - progress) : null,
                message + "（已消耗 1 次提问机会）",
                refreshed,
                null);
        vo.setRemainingFormalAttempts(refreshed.getRemainingFormalAttempts());
        return vo;
    }

    private String buildProgressGuidanceMessage(double progress, double progressGap, List<String> hints) {
        if (hints == null || hints.isEmpty()) {
            return String.format(
                    "主线推理较完整，还差约 %.0f%% 进度可通关（当前 %.0f%%）。请继续提问。",
                    progressGap, progress);
        }
        return String.format(
                "主线推理较完整，还差约 %.0f%% 可通关。请按指引用「提问」继续盘（不扣问次）。",
                progressGap);
    }

    private String buildFormalPartialMessage(
            CoverageEvaluation evaluation, double progress, TheoryPartialReason reason) {
        if (reason == TheoryPartialReason.PROGRESS_GAP) {
            double gap = roundGap(properties.getWinProgress() - progress);
            if (evaluation.missingTaskNames().isEmpty()) {
                return String.format(
                        "推理方向接近，但通关还需进度 %.0f%%（当前 %.0f%%，还差约 %.0f%%），请继续提问解锁任务",
                        properties.getWinProgress(), progress, gap);
            }
            return String.format(
                    "部分正确，仍需解释：%s；且通关还需进度 %.0f%%（还差约 %.0f%%）",
                    String.join("、", evaluation.missingTaskNames()),
                    properties.getWinProgress(), gap);
        }
        if (evaluation.missingTaskNames().isEmpty()) {
            return "方向接近，但还不足以宣告通关，请继续完善推理";
        }
        return "部分正确，仍需解释：" + String.join("、", evaluation.missingTaskNames());
    }

    private static double roundGap(double gap) {
        return Math.max(0, Math.ceil(gap));
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

    private void assertMemberForTeamVote(GameSession session, Long userId) {
        if (session.getPlayMode() != PlayMode.MULTI) {
            return;
        }
        String roomId = chatGameRepository.findFirstByGameSessionId(session.getSessionId())
                .map(game -> game.getRoomId())
                .orElseThrow(() -> new BusinessException(404, "未找到关联大厅"));
        ChatGameMember member = chatGameMemberRepository
                .findById(new ChatGameMemberId(userId, roomId))
                .orElse(null);
        if (member == null) {
            throw new BusinessException(403, "推理提交者不是该房间成员");
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
            List<String> hints,
            TheoryPartialReason partialReason,
            Double progressGap,
            String message,
            TheoryUnlockVO unlock,
            EndGameVO endGame) {
        SubmitTheoryVO vo = new SubmitTheoryVO();
        vo.setStatus(status);
        vo.setCoverageScore(coverageScore != null
                ? BigDecimal.valueOf(coverageScore).setScale(2, RoundingMode.HALF_UP).doubleValue()
                : null);
        vo.setMissingTasks(missingTasks == null || missingTasks.isEmpty() ? null : missingTasks);
        vo.setHints(hints == null || hints.isEmpty() ? null : hints);
        vo.setPartialReason(partialReason);
        vo.setProgressGap(progressGap);
        vo.setMessage(message);
        vo.setTheoryUnlock(unlock);
        vo.setRemainingFormalAttempts(unlock != null ? unlock.getRemainingFormalAttempts() : null);
        vo.setEndGame(endGame);
        return vo;
    }

    private record CoverageEvaluation(double coverageScore, List<String> missingTaskNames) {
    }
}
