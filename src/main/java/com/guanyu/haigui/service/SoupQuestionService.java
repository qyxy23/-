package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.Info.SoupInfo;
import com.guanyu.haigui.pojo.dto.SoupQuestionRequest;
import com.guanyu.haigui.pojo.vo.SoupQuestionResponse;

/**
 * 海龟汤问题处理服务接口
 * 提供基于向量匹配的智能问答判断功能
 */
public interface SoupQuestionService {

    /**
     * 处理海龟汤问题
     * 通过向量匹配相关线索，调用AI进行判断
     *
     * @param request 问题请求
     * @return 判断响应
     */
    // SoupQuestionResponse processSoupQuestion(SoupQuestionRequest request);
    String processSoupQuestion(SoupQuestionRequest request);

    /**
     * 判断请求参数是否有效
     *
     * @param request 问题请求
     * @return 是否有效
     */
    boolean validateRequest(SoupQuestionRequest request);

    /**
     * 向量化问题文本
     *
     * @param question 问题文本
     * @return 向量数据
     */
    java.util.List<Float> vectorizeQuestion(String question);

    /**
     * 在海龟汤范围内搜索相关线索
     *
     * @param soupId 海龟汤ID
     * @param questionVector 问题向量
     * @param topK 返回数量
     * @param minSimilarity 最小相似度
     * @param question 原始问题文本
     * @return 匹配的线索信息
     */
    java.util.Map<String, java.util.List<VectorService.ContextMatchResult>> searchRelevantClues(
            String soupId,
            java.util.List<Float> questionVector,
            int topK,
            double minSimilarity,
            String question
    );

    /**
     * 构建AI判断的提示词
     *
     * @param question 原问题
     * @param relevantClues 相关线索
     * @param soupInfo 海龟汤信息
     * @return AI提示词
     */
    String buildAIPrompt(String question,
                        java.util.Map<String, java.util.List<VectorService.ContextMatchResult>> relevantClues,
                        SoupInfo soupInfo);

    /**
     * 调用AI生成判断结果
     *
     * @param prompt AI提示词
     * @return AI回答
     */
    String generateAIResponse(String prompt);

    /**
     * 解析AI回答，提取判断结果
     *
     * @param aiResponse AI回答
     * @return 判断结果 (YES/NO/PARTIAL/UNKNOWN)
     */
    String parseAnswer(String aiResponse);

    /**
     * 获取海龟汤基本信息
     *
     * @param soupId 海龟汤ID
     * @return 海龟汤信息
     */
    SoupInfo getSoupInfo(String soupId);

    /**
     * 记录问答统计信息
     *
     * @param soupId 海龟汤ID
     * @param userId 用户ID
     * @param question 问题
     * @param answer 回答
     * @param similarity 最高相似度
     */
    void recordQuestionStats(String soupId, Long userId, String question, String answer, Double similarity);

    SoupQuestionResponse processSoupQuestion1(SoupQuestionRequest request);

}