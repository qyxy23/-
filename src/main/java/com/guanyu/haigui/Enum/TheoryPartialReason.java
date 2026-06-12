package com.guanyu.haigui.Enum;

import lombok.Getter;

/**
 * 提交推理为 PARTIAL 时的主要原因（供前端区分展示）
 */
@Getter
public enum TheoryPartialReason {
    /** 已完成任务 coverage 不足 */
    COVERAGE_GAP("推理覆盖不足"),
    /** 进度未达判胜线，提示继续盘未完成主线 */
    PROGRESS_GAP("进度未达通关线");

    private final String description;

    TheoryPartialReason(String description) {
        this.description = description;
    }
}
