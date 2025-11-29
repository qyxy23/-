package com.guanyu.haigui.pojo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 推理任务DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InferenceTaskDTO {

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 海龟汤ID
     */
    private String soupId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务描述
     */
    private String description;

    /**
     * 理解层次
     */
    private Integer understandingLevel;

    /**
     * 目标关键词列表
     */
    private List<String> targetKeywords;

    /**
     * 推理目标（AI判断标准）
     */
    private String reasoningGoal;

    /**
     * 进度权重
     */
    private Double progressWeight;

    /**
     * 是否必须完成
     */
    private Boolean isMandatory;

    /**
     * 任务顺序
     */
    private Integer taskOrder;

    /**
     * 创建时间
     */
    private Long createdAt;

    /**
     * 更新时间
     */
    private Long updatedAt;

    /**
     * 是否已删除
     */
    private Boolean isDeleted;
}