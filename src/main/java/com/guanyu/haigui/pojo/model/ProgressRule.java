package com.guanyu.haigui.pojo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 进度规则模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgressRule {
    private Integer playerCount; // 玩家数量
    private Integer maxRounds; // 最大回合数
    private Integer timeLimit; // 时间限制（分钟）
    private Boolean allowSkip; // 是否允许跳过
    private String winCondition; // 胜利条件：all_clues(所有线索)、main_clues(主线线索)、custom(自定义)
    private Integer requiredClueCount; // 需要的线索数量
    private String difficulty; // 难度：easy、medium、hard
}