package com.guanyu.haigui.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 线索AI分析结果
 * 存储AI对用户线索的分析结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClueAnalysisResult {

    /**
     * 线索内容
     */
    private String content;

    /**
     * 线索类型
     * TIME: 时间
     * PLACE: 地点
     * CHARACTER: 人物
     * OBJECT: 物品
     * PLOT: 情节
     * TRUTH: 真相
     */
    private String type;

    /**
     * 难度等级 (1-5)
     * 1: 非常简单，几乎不需要推理
     * 2: 简单，需要少量推理
     * 3: 中等，需要明显推理
     * 4: 困难，需要深入推理
     * 5: 非常困难，需要复杂推理
     */
    private Integer difficulty;

    /**
     * 是否关键线索
     */
    private Boolean isCore;

    /**
     * 关联的推理任务层级
     */
    private Integer associatedTask;

    /**
     * 重要性评分 (1-10)
     */
    private Integer importance;

    /**
     * 分析理由
     */
    private String reasoning;

    /**
     * 获取类型描述
     */
    public String getTypeDescription() {
        switch (type) {
            case "TIME": return "时间";
            case "PLACE": return "地点";
            case "CHARACTER": return "人物";
            case "OBJECT": return "物品";
            case "PLOT": return "情节";
            case "TRUTH": return "真相";
            default: return type;
        }
    }

    /**
     * 获取难度描述
     */
    public String getDifficultyDescription() {
        if (difficulty == null) return "未知";
        switch (difficulty) {
            case 1: return "非常简单";
            case 2: return "简单";
            case 3: return "中等";
            case 4: return "困难";
            case 5: return "非常困难";
            default: return "难度" + difficulty;
        }
    }

    /**
     * 获取重要性等级
     */
    public String getImportanceLevel() {
        if (importance == null) return "未知";
        if (importance <= 3) return "低";
        if (importance <= 6) return "中";
        if (importance <= 8) return "高";
        return "极高";
    }

    /**
     * 是否为高难度线索
     */
    public boolean isHighDifficulty() {
        return difficulty != null && difficulty >= 4;
    }

    /**
     * 是否为核心线索
     */
    public boolean isKeyClue() {
        return Boolean.TRUE.equals(isCore);
    }
}