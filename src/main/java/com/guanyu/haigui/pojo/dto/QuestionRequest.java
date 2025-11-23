package com.guanyu.haigui.pojo.dto;

import lombok.Data;

/**
 * 玩家问题请求DTO
 */
@Data
public class QuestionRequest {

    /**
     * 游戏会话ID
     */
    private String sessionId;

    /**
     * 海龟汤ID
     */
    private String soupId;

    /**
     * 玩家问题
     */
    private String question;

    /**
     * 玩家ID（从上下文获取，可选）
     */
    private Long userId;

    /**
     * 当前游戏进度（可选，用于上下文构建）
     */
    private String currentProgress;

    /**
     * 检索相关上下文的最大数量（可选，默认5）
     */
    private Integer topK = 5;

    /**
     * 是否需要详细上下文信息（可选，默认false）
     */
    private Boolean includeContext = false;
}