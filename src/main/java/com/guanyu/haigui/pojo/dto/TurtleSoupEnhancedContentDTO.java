package com.guanyu.haigui.pojo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 海龟汤增强内容DTO
 * 包含AI生成的线索片段和推理任务
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TurtleSoupEnhancedContentDTO {

    /**
     * 汤面标题
     */
    private String soupTitle;

    /**
     * 汤面内容
     */
    private String soupSurface;

    /**
     * 汤底内容
     */
    private String soupBottom;

    /**
     * 用户线索（如果有）
     */
    private List<Map<String, Object>> userClues;

    /**
     * AI生成的线索片段列表
     */
    private List<ClueFragmentDTO> clueFragments;

    /**
     * AI生成的推理任务列表
     */
    private List<InferenceTaskDTO> inferenceTasks;

    /**
     * 生成策略类型
     */
    private String generationStrategy;

    /**
     * 生成时间戳
     */
    private Long generatedAt;
}