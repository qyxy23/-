package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.ClueType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 线索片段DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClueFragmentDTO {

    /**
     * 线索片段ID
     */
    private Long fragmentId;

    /**
     * 海龟汤ID
     */
    private String soupId;

    /**
     * 线索片段内容
     */
    private String fragmentContent;

    /**
     * 线索片段类型
     */
    private ClueType fragmentType;

    /**
     * 推理层次
     */
    private Integer inferenceLevel;

    /**
     * 难度
     */
    private Integer difficulty;

    /**
     * 重要性
     */
    private Integer importance;

    /**
     * 关键词列表
     */
    private List<String> triggerKeywords;

    /**
     * 是否为核心线索
     */
    private Boolean isCoreClue;

    /**
     * 相似度阈值
     */
    private BigDecimal similarityThreshold;

    /**
     * 关联的任务ID列表
     */
    private List<Integer> associatedTaskIds;

    /**
     * 线索片段顺序
     */
    private Integer fragmentOrder;

    /**
     * 生成来源
     */
    private String generationSource;

    /**
     * AI分析置信度
     */
    private Double aiAnalysisConfidence;

    /**
     * 向量哈希
     */
    private String vectorHash;

    /**
     * 是否已删除
     */
    private Boolean isDeleted;
}