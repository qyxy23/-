package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.dto.QuestionRequest;
import com.guanyu.haigui.pojo.dto.QuestionResponse;
import com.guanyu.haigui.pojo.result.ContextMatchResult;

import java.util.Map;

/**
 * 游戏问题处理服务接口
 * 处理玩家在海龟汤游戏中的问题，包括向量检索和AI回答
 */
public interface GameQuestionService {

    /**
     * 处理玩家问题
     * 完整流程：向量化 → 检索 → 构建上下文 → AI回答
     *
     * @param questionRequest 问题请求
     * @return 问题回答
     */
    QuestionResponse processPlayerQuestion(QuestionRequest questionRequest);

    /**
     * 向量化玩家问题
     *
     * @param question 玩家问题
     * @return 问题向量
     */
    java.util.List<Float> vectorizeQuestion(String question);

    /**
     * 检索相关上下文
     *
     * @param questionVector 问题向量
     * @param soupId 海龟汤ID
     * @param topK 返回前K个相关上下文
     * @return 相关上下文映射（按类型分组）
     */
    Map<String, java.util.List<ContextMatchResult>> retrieveRelevantContext(
            java.util.List<Float> questionVector, String soupId, int topK);

    /**
     * 构建AI提示词
     *
     * @param question 玩家原始问题
     * @param contextMap 相关上下文
     * @param soupInfo 海龟汤基本信息
     * @return AI提示词
     */
    String buildPromptForAI(String question, Map<String, java.util.List<ContextMatchResult>> contextMap, SoupInfo soupInfo);

    /**
     * 调用AI生成回答
     *
     * @param prompt AI提示词
     * @return AI回答
     */
    String generateAIResponse(String prompt);

    /**
     * 记录对话统计
     *
     * @param sessionId 游戏会话ID
     * @param userId 玩家ID
     * @param isYesAnswer 是否为"是"的回答
     */
    void recordDialogStats(String sessionId, Long userId, boolean isYesAnswer);

    /**
     * 获取游戏的上下文信息
     *
     * @param soupId 海龟汤ID
     * @return 海龟汤信息
     */
    SoupInfo getSoupInfo(String soupId);

    /**
     * 海龟汤信息类
     */
    class SoupInfo {
        private String soupId;
        private String soupTitle;
        private String soupSurface;
        private String currentProgress;

        public SoupInfo(String soupId, String soupTitle, String soupSurface, String currentProgress) {
            this.soupId = soupId;
            this.soupTitle = soupTitle;
            this.soupSurface = soupSurface;
            this.currentProgress = currentProgress;
        }

        // Getters
        public String getSoupId() { return soupId; }
        public String getSoupTitle() { return soupTitle; }
        public String getSoupSurface() { return soupSurface; }
        public String getCurrentProgress() { return currentProgress; }
    }
}