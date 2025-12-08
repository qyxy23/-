package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.pojo.result.ContextMatchResult;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 玩家问题回答响应DTO
 */
@Data
public class QuestionResponse {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * AI回答
     */
    private String answer;

    /**
     * 回答类型：YES/NO/MAYBE/DETAIL
     */
    private String answerType;

    /**
     * 相似度分数（最高匹配度）
     */
    private Double maxSimilarity;

    /**
     * 检索到的相关上下文（如果includeContext=true）
     */
    private Map<String, List<ContextMatchResult>> relevantContext;

    /**
     * 处理耗时（毫秒）
     */
    private Long processingTime;

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;

    /**
     * 会话信息
     */
    private SessionInfo sessionInfo;

    /**
     * 会话信息内嵌类
     */
    @Data
    public static class SessionInfo {
        private String sessionId;
        private String soupId;
        private String soupTitle;
        private String currentProgress;

        public SessionInfo(String sessionId, String soupId, String soupTitle, String currentProgress) {
            this.sessionId = sessionId;
            this.soupId = soupId;
            this.soupTitle = soupTitle;
            this.currentProgress = currentProgress;
        }
    }

    /**
     * 创建成功响应
     */
    public static QuestionResponse success(String answer, String answerType, Double maxSimilarity) {
        QuestionResponse response = new QuestionResponse();
        response.setSuccess(true);
        response.setAnswer(answer);
        response.setAnswerType(answerType);
        response.setMaxSimilarity(maxSimilarity);
        return response;
    }

    /**
     * 创建失败响应
     */
    public static QuestionResponse failure(String errorMessage) {
        QuestionResponse response = new QuestionResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }

    /**
     * 检查是否为"是"的回答
     */
    public boolean isYesAnswer() {
        return "YES".equalsIgnoreCase(answerType);
    }

    /**
     * 检查是否为"否"的回答
     */
    public boolean isNoAnswer() {
        return "NO".equalsIgnoreCase(answerType);
    }
}