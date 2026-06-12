package com.guanyu.haigui.utils;

import com.guanyu.haigui.Enum.ContentTone;
import com.guanyu.haigui.Enum.LogicMode;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.pojo.Info.ClueFragmentInfo;
import com.guanyu.haigui.pojo.Info.InferenceTaskInfo;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 审核草稿（线索 / 任务）发布前校验
 */
public final class AuditDraftValidator {

    private AuditDraftValidator() {
    }

    /** 发布前须填写汤的逻辑框架与内容气质（元问题快路径判题） */
    public static void validateSoupMeta(LogicMode logicMode, ContentTone contentTone) {
        if (logicMode == null) {
            throw new BusinessException(400, "请选择本格/变格");
        }
        if (contentTone == null) {
            throw new BusinessException(400, "请选择清汤/红汤/黑汤");
        }
    }

    /**
     * 校验每个推理任务至少配置 1 条有效前置线索（序号为 1-based，对应 fragments 列表下标）
     */
    public static void validateTaskPrerequisites(List<ClueFragmentInfo> fragments,
                                                 List<InferenceTaskInfo> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new BusinessException(400, "推理任务不能为空");
        }
        int fragmentCount = fragments == null ? 0 : fragments.size();
        if (fragmentCount == 0) {
            throw new BusinessException(400, "线索片段不能为空");
        }

        List<String> errors = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            InferenceTaskInfo task = tasks.get(i);
            if (task == null) {
                errors.add(String.format("任务 %d 数据无效", i + 1));
                continue;
            }
            String label = StringUtils.hasText(task.getTaskName())
                    ? task.getTaskName().trim() : "未命名";
            List<Long> allIds = normalizePrerequisiteIds(task.getPrerequisiteFragmentIds());

            List<Long> validIds = allIds.stream()
                    .filter(id -> id >= 1 && id <= fragmentCount)
                    .collect(Collectors.toList());
            if (validIds.isEmpty()) {
                errors.add(String.format("任务 %d「%s」未选择前置线索", i + 1, label));
            }

            List<Long> invalidIds = allIds.stream()
                    .filter(id -> id < 1 || id > fragmentCount)
                    .collect(Collectors.toList());
            if (!invalidIds.isEmpty()) {
                String joined = invalidIds.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining("、"));
                errors.add(String.format("任务 %d「%s」含无效线索序号：%s", i + 1, label, joined));
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(400,
                    String.join("\n", errors) + "\n请为每个任务配置至少 1 条有效前置线索。");
        }
    }

    private static List<Long> normalizePrerequisiteIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
