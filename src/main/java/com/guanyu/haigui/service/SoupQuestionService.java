package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.Info.SoupInfo;
import com.guanyu.haigui.pojo.response.AIResponse;
import com.guanyu.haigui.pojo.result.ChatWithAIRoomRequest;
import com.guanyu.haigui.pojo.result.ContextMatchResult;
import com.guanyu.haigui.pojo.vo.RoomSoupQuestionVO;

/**
 * 海龟汤问题处理服务接口
 * 提供基于向量匹配的智能问答判断功能
 */
public interface SoupQuestionService {
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
    java.util.Map<String, java.util.List<ContextMatchResult>> searchRelevantClues(
            String soupId,
            java.util.List<Float> questionVector,
            int topK,
            double minSimilarity,
            String question
    );


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
    AIResponse parseAnswer(String aiResponse);

    /**
     * 获取海龟汤基本信息
     *
     * @param soupId 海龟汤ID
     * @return 海龟汤信息
     */
    SoupInfo getSoupInfo(String soupId);


    // SoupQuestionResponse processSoupQuestion1(SoupQuestionRequest request);

    RoomSoupQuestionVO RoomProcessSoupQuestion(ChatWithAIRoomRequest request);

}