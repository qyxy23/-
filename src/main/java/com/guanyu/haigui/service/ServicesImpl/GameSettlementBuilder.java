package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.result.ClueSummaryView;
import com.guanyu.haigui.pojo.result.GameSettlementSnapshot;
import com.guanyu.haigui.pojo.result.SettlementTaskView;
import com.guanyu.haigui.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GameSettlementBuilder {

    private final ChatGameRepository chatGameRepository;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final InferenceTaskRepository inferenceTaskRepository;
    private final ClueFragmentRepository clueFragmentRepository;
    private final HaiGuiRoomProgressRepository haiGuiRoomProgressRepository;

    public GameSettlementSnapshot build(String roomId) {
        ChatGame chatGame = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(404, "房间不存在"));
        HaiGuiSoup soup = haiGuiSoupRepository.findById(chatGame.getHaiGuiSoup().getSoupId())
                .orElseThrow(() -> new BusinessException(404, "该海龟汤不存在"));

        List<HaiGuiRoomProgress> progressList = haiGuiRoomProgressRepository.findByRoomId(roomId);
        Map<Long, HaiGuiRoomProgress> progressMap = progressList.stream()
                .collect(Collectors.toMap(HaiGuiRoomProgress::getTaskId, Function.identity(), (a, b) -> a));

        List<InferenceTask> allTasks = inferenceTaskRepository.findBySoupId(soup.getSoupId());
        List<ClueFragment> allClues = clueFragmentRepository.findBySoupIdAndIsDeletedFalse(soup.getSoupId());

        Set<Long> triggeredIds = new HashSet<>();
        for (HaiGuiRoomProgress progress : progressList) {
            if (progress.getTriggeredFragmentIds() != null) {
                triggeredIds.addAll(progress.getTriggeredFragmentIds());
            }
        }

        GameSettlementSnapshot snapshot = new GameSettlementSnapshot();
        snapshot.setRoomId(roomId);
        snapshot.setSoupTitle(soup.getSoupTitle());
        snapshot.setSoupBottom(soup.getSoupBottom());

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal completedWeight = BigDecimal.ZERO;

        for (InferenceTask task : allTasks) {
            HaiGuiRoomProgress progress = progressMap.get(task.getTaskId());
            boolean isCompleted = progress != null && Boolean.TRUE.equals(progress.getCompleted());
            SettlementTaskView taskView = toTaskView(task);

            if (isCompleted) {
                completedWeight = completedWeight.add(task.getProgressWeight());
                snapshot.getCompletedTasks().add(taskView);
            } else {
                snapshot.getUncompletedTasks().add(taskView);
            }
            totalWeight = totalWeight.add(task.getProgressWeight());
        }

        BigDecimal progressPercent = BigDecimal.ZERO;
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            progressPercent = completedWeight.divide(totalWeight, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        snapshot.setProgressPercent(progressPercent);
        snapshot.setFinalScore(progressPercent.setScale(0, RoundingMode.HALF_UP).intValueExact());

        for (ClueFragment clue : allClues) {
            ClueSummaryView clueView = new ClueSummaryView();
            clueView.setContent(clue.getFragmentContent());
            if (triggeredIds.contains(clue.getFragmentId())) {
                snapshot.getTriggeredClues().add(clueView);
            } else {
                snapshot.getMissedClues().add(clueView);
            }
        }

        return snapshot;
    }

    private static SettlementTaskView toTaskView(InferenceTask task) {
        SettlementTaskView view = new SettlementTaskView();
        view.setTaskName(task.getTaskName());
        view.setDescription(task.getTaskDescription());
        return view;
    }
}
