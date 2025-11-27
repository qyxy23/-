package com.guanyu.haigui.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 线索匹配结果
 * 用于在海龟汤中搜索相关线索的返回结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClueMatchResult {

    /**
     * 片段ID
     */
    private String fragmentId;

    /**
     * 片段内容（线索文本）
     */
    private String fragmentContent;

    /**
     * 片段类型
     */
    private String fragmentType;

    /**
     * 是否为核心线索
     */
    private Boolean isCoreClue;

    /**
     * 推理层级
     */
    private Integer inferenceLevel;

    /**
     * 与查询的相似度分数（0-1之间）
     */
    private Double similarity;

    /**
     * 海龟汤ID
     */
    private String soupId;

    /**
     * 匹配原因说明
     */
    private String matchReason;

    /**
     * 生成来源
     */
    private String generationSource;

    /**
     * AI分析置信度
     */
    private Double aiAnalysisConfidence;

    /**
     * 构造函数
     */
    public ClueMatchResult(String fragmentId, String fragmentContent, String fragmentType,
                         Boolean isCoreClue, Integer inferenceLevel, Double similarity,
                         String soupId, String matchReason) {
        this.fragmentId = fragmentId;
        this.fragmentContent = fragmentContent;
        this.fragmentType = fragmentType;
        this.isCoreClue = isCoreClue;
        this.inferenceLevel = inferenceLevel;
        this.similarity = similarity;
        this.soupId = soupId;
        this.matchReason = matchReason;
    }

    /**
     * 获取片段类型描述
     */
    public String getFragmentTypeDescription() {
        if (fragmentType == null) return "未知";
        switch (fragmentType) {
            case "TIME": return "时间";
            case "PLACE": return "地点";
            case "CHARACTER": return "人物";
            case "PLOT": return "情节";
            case "OBJECT": return "物品";
            case "TRUTH": return "真相";
            default: return fragmentType;
        }
    }

    /**
     * 获取推理层级描述
     */
    public String getInferenceLevelDescription() {
        if (inferenceLevel == null) return "未知";
        switch (inferenceLevel) {
            case 1: return "表层信息";
            case 2: return "浅层推理";
            case 3: return "中层推理";
            case 4: return "深层推理";
            case 5: return "核心真相";
            default: return "层级" + inferenceLevel;
        }
    }

    /**
     * 判断是否为高匹配度
     */
    public boolean isHighMatch() {
        return similarity != null && similarity > 0.7;
    }

    /**
     * 判断是否为中等匹配度
     */
    public boolean isMediumMatch() {
        return similarity != null && similarity > 0.4 && similarity <= 0.7;
    }

    /**
     * 获取匹配度等级
     */
    public String getMatchLevel() {
        if (similarity == null) return "未知";
        if (similarity > 0.8) return "高度匹配";
        if (similarity > 0.6) return "中度匹配";
        if (similarity > 0.4) return "低度匹配";
        return "弱相关";
    }
}