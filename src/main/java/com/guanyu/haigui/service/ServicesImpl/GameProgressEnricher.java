package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.PlayMode;
import com.guanyu.haigui.config.TheorySubmissionProperties;
import com.guanyu.haigui.pojo.model.GameSession;
import com.guanyu.haigui.pojo.model.HaiGuiGameProgress;
import com.guanyu.haigui.pojo.model.InferenceTask;
import com.guanyu.haigui.pojo.result.ClueSummaryView;
import com.guanyu.haigui.pojo.result.SettlementTaskView;
import com.guanyu.haigui.pojo.vo.RoomGetClueVO;
import com.guanyu.haigui.pojo.vo.TheoryUnlockVO;
import com.guanyu.haigui.pojo.vo.MemberContributionView;
import com.guanyu.haigui.repository.ClueFragmentRepository;
import com.guanyu.haigui.repository.HaiGuiChatMessageRepository;
import com.guanyu.haigui.repository.HaiGuiGameProgressRepository;
import com.guanyu.haigui.repository.HaiGuiTheorySubmissionRepository;
import com.guanyu.haigui.repository.InferenceTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GameProgressEnricher {

    private final HaiGuiGameProgressRepository haiGuiGameProgressRepository;
    private final InferenceTaskRepository inferenceTaskRepository;
    private final ClueFragmentRepository clueFragmentRepository;
    private final HaiGuiChatMessageRepository haiGuiChatMessageRepository;
    private final HaiGuiTheorySubmissionRepository haiGuiTheorySubmissionRepository;
    private final TheorySubmissionProperties theorySubmissionProperties;
    private final MemberContributionAggregator memberContributionAggregator;

    public void enrichInGameProgress(RoomGetClueVO vo, GameSession session) {
        if (vo == null || session == null) {
            return;
        }
        if (session.getStatus() != GameSession.GameSessionStatus.ONGOING) {
            return;
        }

        String gameSessionId = session.getSessionId();
        List<HaiGuiGameProgress> progressList =
                haiGuiGameProgressRepository.findByGameSessionId(gameSessionId);
        Map<Long, HaiGuiGameProgress> progressMap = progressList.stream()
                .collect(Collectors.toMap(HaiGuiGameProgress::getTaskId, Function.identity(), (a, b) -> a));

        List<InferenceTask> allTasks = inferenceTaskRepository.findBySoupId(session.getSoupId());

        Set<Long> triggeredIds = new HashSet<>();
        int completedTaskCount = 0;
        List<SettlementTaskView> completedTasks = new ArrayList<>();

        for (InferenceTask task : allTasks) {
            HaiGuiGameProgress progress = progressMap.get(task.getTaskId());
            boolean completed = progress != null && Boolean.TRUE.equals(progress.getCompleted());
            if (progress != null && progress.getTriggeredFragmentIds() != null) {
                triggeredIds.addAll(progress.getTriggeredFragmentIds());
            }
            if (completed) {
                completedTaskCount++;
                SettlementTaskView taskView = new SettlementTaskView();
                taskView.setTaskName(task.getTaskName());
                taskView.setDescription(task.getTaskDescription());
                completedTasks.add(taskView);
            }
        }

        List<ClueSummaryView> triggeredClues = new ArrayList<>();
        if (!triggeredIds.isEmpty()) {
            clueFragmentRepository.findByFragmentIdInAndIsDeletedFalse(new ArrayList<>(triggeredIds))
                    .forEach(fragment -> {
                        ClueSummaryView clueView = new ClueSummaryView();
                        clueView.setContent(fragment.getFragmentContent());
                        triggeredClues.add(clueView);
                    });
        }

        int questionCount = haiGuiChatMessageRepository.findAllByGameSessionId(gameSessionId).size();
        long formalUsed = haiGuiTheorySubmissionRepository.countByGameSessionIdAndFormalAttemptTrue(gameSessionId);

        vo.setTriggeredClues(triggeredClues.isEmpty() ? null : triggeredClues);
        vo.setCompletedTasks(completedTasks.isEmpty() ? null : completedTasks);
        vo.setTheoryUnlock(buildUnlock(session, questionCount, triggeredIds.size(), completedTaskCount, formalUsed));

        if (session.getPlayMode() == PlayMode.MULTI) {
            List<MemberContributionView> contributions = memberContributionAggregator.buildForGameSession(session);
            vo.setMemberContributions(contributions.isEmpty() ? null : contributions);
        }
    }

    public TheoryUnlockVO buildUnlock(
            GameSession session,
            int questionCount,
            int triggeredClueCount,
            int completedTaskCount,
            long formalAttemptsUsed) {
        TheoryUnlockVO unlock = new TheoryUnlockVO();
        unlock.setUnlockProgressRequired(theorySubmissionProperties.getUnlockProgress());
        unlock.setWinProgressRequired(theorySubmissionProperties.getWinProgress());
        unlock.setQuestionCount(questionCount);
        unlock.setTriggeredClueCount(triggeredClueCount);
        unlock.setCompletedTaskCount(completedTaskCount);
        unlock.setCurrentProgress(session.getCurrentProgress().doubleValue());
        unlock.setRemainingFormalAttempts(Math.max(
                0,
                theorySubmissionProperties.getMaxFormalAttempts() - (int) formalAttemptsUsed));

        double progress = session.getCurrentProgress().doubleValue();
        List<String> missing = new ArrayList<>();
        if (progress < theorySubmissionProperties.getUnlockProgress()) {
            missing.add(String.format("进度需达到 %.0f%%（当前 %.0f%%）",
                    theorySubmissionProperties.getUnlockProgress(), progress));
        }
        if (completedTaskCount < 1) {
            missing.add("至少完成 1 个推理任务");
        }
        if (triggeredClueCount < theorySubmissionProperties.getMinTriggeredClues()) {
            missing.add(String.format("至少触发 %d 条线索（当前 %d 条）",
                    theorySubmissionProperties.getMinTriggeredClues(), triggeredClueCount));
        }
        if (questionCount < theorySubmissionProperties.getMinQuestions()) {
            missing.add(String.format("至少提问 %d 次（当前 %d 次）",
                    theorySubmissionProperties.getMinQuestions(), questionCount));
        }
        if (unlock.getRemainingFormalAttempts() <= 0) {
            missing.add("本局正式提交次数已用完");
        }

        if (missing.isEmpty()) {
            unlock.setTheorySubmitEnabled(true);
            unlock.setLockReason(null);
        } else {
            unlock.setTheorySubmitEnabled(false);
            unlock.setLockReason(String.join("；", missing));
        }
        return unlock;
    }

    public InGameProgressSnapshot snapshot(GameSession session) {
        String gameSessionId = session.getSessionId();
        List<HaiGuiGameProgress> progressList =
                haiGuiGameProgressRepository.findByGameSessionId(gameSessionId);
        Map<Long, HaiGuiGameProgress> progressMap = progressList.stream()
                .collect(Collectors.toMap(HaiGuiGameProgress::getTaskId, Function.identity(), (a, b) -> a));

        List<InferenceTask> allTasks = inferenceTaskRepository.findBySoupId(session.getSoupId());
        Set<Long> triggeredIds = new HashSet<>();
        int completedTaskCount = 0;
        List<InferenceTask> completedTasks = new ArrayList<>();

        for (InferenceTask task : allTasks) {
            HaiGuiGameProgress progress = progressMap.get(task.getTaskId());
            if (progress != null && progress.getTriggeredFragmentIds() != null) {
                triggeredIds.addAll(progress.getTriggeredFragmentIds());
            }
            if (progress != null && Boolean.TRUE.equals(progress.getCompleted())) {
                completedTaskCount++;
                completedTasks.add(task);
            }
        }

        int questionCount = haiGuiChatMessageRepository.findAllByGameSessionId(gameSessionId).size();
        long formalUsed = haiGuiTheorySubmissionRepository.countByGameSessionIdAndFormalAttemptTrue(gameSessionId);

        return new InGameProgressSnapshot(
                triggeredIds,
                completedTasks,
                completedTaskCount,
                questionCount,
                formalUsed,
                buildUnlock(session, questionCount, triggeredIds.size(), completedTaskCount, formalUsed)
        );
    }

    public record InGameProgressSnapshot(
            Set<Long> triggeredFragmentIds,
            List<InferenceTask> completedTasks,
            int completedTaskCount,
            int questionCount,
            long formalAttemptsUsed,
            TheoryUnlockVO theoryUnlock
    ) {
    }

    public static double toPercent(BigDecimal progress) {
        if (progress == null) {
            return 0.0;
        }
        return progress.doubleValue();
    }
}
