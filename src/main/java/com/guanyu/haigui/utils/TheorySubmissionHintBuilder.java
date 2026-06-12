package com.guanyu.haigui.utils;

import com.guanyu.haigui.pojo.model.InferenceTask;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 灰区（进度已解锁但未达判胜线）根据未完成任务生成盘汤指引。
 */
public final class TheorySubmissionHintBuilder {

    private TheorySubmissionHintBuilder() {
    }

    /**
     * 取权重最高的未完成任务，生成 1～maxHints 条「建议追问」。
     */
    public static List<String> buildFromIncompleteTasks(List<InferenceTask> incompleteTasks, int maxHints) {
        if (incompleteTasks == null || incompleteTasks.isEmpty() || maxHints <= 0) {
            return List.of();
        }
        List<InferenceTask> sorted = incompleteTasks.stream()
                .sorted(Comparator
                        .comparing((InferenceTask task) -> weightOf(task)).reversed()
                        .thenComparing(task -> task.getTaskOrder() != null ? task.getTaskOrder() : 0))
                .toList();

        List<String> hints = new ArrayList<>();
        for (InferenceTask task : sorted) {
            if (hints.size() >= maxHints) {
                break;
            }
            String hint = toHintLine(task);
            if (StringUtils.hasText(hint)) {
                hints.add(hint);
            }
        }
        return hints;
    }

    /** 返回纯问句（前端展示时再加「建议追问」等样式） */
    private static String toHintLine(InferenceTask task) {
        String prompt = firstNonBlank(task.getTaskDescription(), task.getTaskName());
        if (!StringUtils.hasText(prompt)) {
            return null;
        }
        prompt = prompt.trim();
        if (prompt.endsWith("?") || prompt.endsWith("？")) {
            return prompt;
        }
        if (prompt.startsWith("是否") || prompt.startsWith("有没有") || prompt.startsWith("是不是")) {
            return prompt + "？";
        }
        return prompt + "？";
    }

    private static double weightOf(InferenceTask task) {
        return task.getProgressWeight() != null ? task.getProgressWeight().doubleValue() : 0.0;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
