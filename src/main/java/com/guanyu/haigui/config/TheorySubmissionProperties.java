package com.guanyu.haigui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "haiqutang.theory-submission")
public class TheorySubmissionProperties {

    /** 解锁提交所需的进度百分比 */
    private double unlockProgress = 60.0;

    /** 规则层直接判胜所需的进度百分比 */
    private double winProgress = 80.0;

    /** 解锁所需最少已触发线索数 */
    private int minTriggeredClues = 3;

    /** 解锁所需最少有效提问数 */
    private int minQuestions = 5;

    /** 每局最多正式提交次数 */
    private int maxFormalAttempts = 2;

    /** 推理文本最短字数（trim 后） */
    private int minTheoryLength = 20;

    /** coverage 低于此值视为拒绝，不占正式配额 */
    private double coverageReject = 0.40;

    /** coverage 高于此值且进度达标可判胜 */
    private double coverageWin = 0.85;

    /** 单任务 reasoningGoal 与推理文本的相似度阈值 */
    private double taskSimilarityThreshold = 0.45;
}
