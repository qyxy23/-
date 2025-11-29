package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.model.InferenceTask;
import com.guanyu.haigui.repository.ClueFragmentRepository;
import com.guanyu.haigui.repository.InferenceTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 修复现有线索片段的任务ID关联问题
 * 为空的associated_task_ids字段设置默认的任务关联
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskIdRepairService {

    private final ClueFragmentRepository clueFragmentRepository;
    private final InferenceTaskRepository inferenceTaskRepository;

    /**
     * 修复指定海龟汤的线索任务ID关联
     * @param soupId 海龟汤ID
     * @return 修复的线索数量
     */
    @Transactional
    public int repairTaskIdsForSoup(String soupId) {
        try {
            log.info("开始修复海龟汤的任务ID关联: soupId={}", soupId);

            // 1. 获取该汤的所有推理任务
            List<InferenceTask> tasks = inferenceTaskRepository.findBySoupIdAndIsDeletedFalseOrderByTaskOrderAsc(soupId);
            Map<Integer, Long> taskOrderToIdMap = new HashMap<>();
            for (InferenceTask task : tasks) {
                taskOrderToIdMap.put(task.getTaskOrder(), task.getTaskId());
            }

            // 如果没有任务，创建默认任务
            if (taskOrderToIdMap.isEmpty()) {
                log.warn("海龟汤没有推理任务，创建默认任务: soupId={}", soupId);
                taskOrderToIdMap = createDefaultTasks(soupId);
            }

            // 2. 获取所有没有任务关联的线索片段
            List<ClueFragment> fragmentsToRepair = clueFragmentRepository.findBySoupIdAndIsDeletedFalse(soupId).stream()
                    .filter(fragment -> fragment.getAssociatedTaskIds() == null || fragment.getAssociatedTaskIds().isEmpty())
                    .toList();

            log.info("找到需要修复的线索片段: soupId={}, 数量={}", soupId, fragmentsToRepair.size());

            int repairedCount = 0;
            for (ClueFragment fragment : fragmentsToRepair) {
                try {
                    // 根据推理层次分配默认任务
                    List<Integer> taskIds = getDefaultTaskIdsForInferenceLevel(
                            fragment.getInferenceLevel(), taskOrderToIdMap);

                    fragment.setAssociatedTaskIds(taskIds);
                    clueFragmentRepository.save(fragment);
                    repairedCount++;

                    log.debug("修复线索片段任务关联: fragmentId={}, inferenceLevel={}, taskIds={}",
                            fragment.getFragmentId(), fragment.getInferenceLevel(), taskIds);

                } catch (Exception e) {
                    log.error("修复线索片段失败: fragmentId={}", fragment.getFragmentId(), e);
                }
            }

            log.info("修复完成: soupId={}, 总线索数={}, 修复数={}",
                    soupId, fragmentsToRepair.size(), repairedCount);
            return repairedCount;

        } catch (Exception e) {
            log.error("修复海龟汤任务ID关联失败: soupId={}", soupId, e);
            return 0;
        }
    }

    /**
     * 修复所有海龟汤的任务ID关联
     * @return 修复的统计信息
     */
    @Transactional
    public Map<String, Integer> repairAllTaskIds() {
        log.info("开始修复所有海龟汤的任务ID关联");

        Map<String, Integer> repairStats = new HashMap<>();

        // 获取所有汤的ID
        List<String> soupIds = clueFragmentRepository.findAllDistinctSoupIds();

        int totalRepaired = 0;
        for (String soupId : soupIds) {
            try {
                int repaired = repairTaskIdsForSoup(soupId);
                repairStats.put(soupId, repaired);
                totalRepaired += repaired;
            } catch (Exception e) {
                log.error("修复海龟汤失败: soupId={}", soupId, e);
                repairStats.put(soupId, 0);
            }
        }

        repairStats.put("totalRepaired", totalRepaired);
        repairStats.put("totalSoups", soupIds.size());

        log.info("修复所有海龟汤完成: 总汤数={}, 总修复线索数={}",
                soupIds.size(), totalRepaired);

        return repairStats;
    }

    /**
     * 创建默认推理任务
     */
    private Map<Integer, Long> createDefaultTasks(String soupId) {
        Map<Integer, Long> taskOrderToIdMap = new HashMap<>();

        try {
            // 创建三个默认任务
            InferenceTask task1 = new InferenceTask(soupId, "发现基本信息", "询问故事的基本背景和设定", 1);
            task1.setReasoningGoal("掌握故事的基本时间、地点、人物等背景信息");
            task1.setProgressWeight(20.0);
            task1.setTaskOrder(1);

            InferenceTask task2 = new InferenceTask(soupId, "理解内在联系", "理解各要素之间的关系", 2);
            task2.setReasoningGoal("理解事件之间的因果关系和人物动机");
            task2.setProgressWeight(30.0);
            task2.setTaskOrder(2);

            InferenceTask task3 = new InferenceTask(soupId, "推理深层真相", "发现隐藏的关键信息", 3);
            task3.setReasoningGoal("揭示故事的完整真相和核心秘密");
            task3.setProgressWeight(50.0);
            task3.setTaskOrder(3);

            // 保存任务
            InferenceTask savedTask1 = inferenceTaskRepository.save(task1);
            InferenceTask savedTask2 = inferenceTaskRepository.save(task2);
            InferenceTask savedTask3 = inferenceTaskRepository.save(task3);

            taskOrderToIdMap.put(1, savedTask1.getTaskId());
            taskOrderToIdMap.put(2, savedTask2.getTaskId());
            taskOrderToIdMap.put(3, savedTask3.getTaskId());

            log.info("创建默认推理任务成功: soupId={}", soupId);

        } catch (Exception e) {
            log.error("创建默认推理任务失败: soupId={}", soupId, e);
        }

        return taskOrderToIdMap;
    }

    /**
     * 根据推理层次获取默认任务ID
     */
    private List<Integer> getDefaultTaskIdsForInferenceLevel(Integer inferenceLevel, Map<Integer, Long> taskOrderToIdMap) {
        List<Integer> taskIds = new ArrayList<>();

        if (inferenceLevel == null || inferenceLevel < 1) {
            inferenceLevel = 1;
        }

        // 根据推理层次分配任务
        // 层次1 -> 任务1
        // 层次2 -> 任务1,2
        // 层次3 -> 任务2,3
        // 层次4 -> 任务3
        switch (inferenceLevel) {
            case 1:
                addTaskIdIfExist(taskIds, 1, taskOrderToIdMap);
                break;
            case 2:
                addTaskIdIfExist(taskIds, 1, taskOrderToIdMap);
                addTaskIdIfExist(taskIds, 2, taskOrderToIdMap);
                break;
            case 3:
                addTaskIdIfExist(taskIds, 2, taskOrderToIdMap);
                addTaskIdIfExist(taskIds, 3, taskOrderToIdMap);
                break;
            case 4:
            default:
                addTaskIdIfExist(taskIds, 3, taskOrderToIdMap);
                break;
        }

        // 如果没有匹配的任务，分配第一个任务
        if (taskIds.isEmpty() && !taskOrderToIdMap.isEmpty()) {
            taskIds.add(taskOrderToIdMap.values().iterator().next().intValue());
        }

        return taskIds;
    }

    /**
     * 添加任务ID如果存在
     */
    private void addTaskIdIfExist(List<Integer> taskIds, int taskOrder, Map<Integer, Long> taskOrderToIdMap) {
        if (taskOrderToIdMap.containsKey(taskOrder)) {
            taskIds.add(taskOrderToIdMap.get(taskOrder).intValue());
        }
    }
}