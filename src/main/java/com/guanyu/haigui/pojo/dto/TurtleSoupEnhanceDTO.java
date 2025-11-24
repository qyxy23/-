package com.guanyu.haigui.pojo.dto;

import lombok.Data;

/**
 * 海龟汤AI增强DTO
 * 支持标题、汤面、汤底、进度设置列表的AI完善功能
 */
@Data
public class TurtleSoupEnhanceDTO {
    /**
     * 标题（可选）
     */
    private String soupTitle;

    /**
     * 汤面（故事背景）
     */
    private String soupSurface;

    /**
     * 汤底（真相解答）
     */
    private String soupBottom;

    /**
     * 用户提前写好的进度设置列表（可选）
     * 格式：JSON字符串，包含进度设置列表
     */
    private String progressTasks;  // 保持原字段名以兼容前端，但内部逻辑使用progressSettings

    /**
     * 用户提前写好的关键线索（可选）
     * 格式：JSON字符串，包含线索列表
     */
    private String keyClues;

    /**
     * 用户提前写好的主持人手册（可选）
     */
    private String hostManual;
}