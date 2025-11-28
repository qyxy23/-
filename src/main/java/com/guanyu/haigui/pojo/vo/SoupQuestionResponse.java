package com.guanyu.haigui.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 海龟汤问题回答响应VO
 * 包含AI判断结果和匹配的线索信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "海龟汤问题回答响应")
public class SoupQuestionResponse {

    @Schema(description = "响应状态", example = "SUCCESS")
    private String status;

    @Schema(description = "响应消息", example = "判断成功")
    private String message;

    @Schema(description = "海龟汤ID", example = "uuid-haigui-soup-id")
    private String soupId;

    @Schema(description = "原问题", example = "凶手是男性吗？")
    private String question;

    @Schema(description = "AI判断结果", allowableValues = {"YES", "NO", "PARTIAL", "UNKNOWN"}, example = "YES")
    private String answer;

    @Schema(description = "AI回答说明", example = "根据线索显示，凶手确实是男性")
    private String explanation;

    @Schema(description = "最高相似度", example = "0.85")
    private Double maxSimilarity;

    @Schema(description = "匹配的线索数量", example = "3")
    private Integer matchedClueCount;

    @Schema(description = "处理时间（毫秒）", example = "150")
    private Long processingTime;

    @Schema(description = "匹配的线索详情")
    private List<ClueMatchInfo> matchedClues;

    @Schema(description = "各类型向量匹配结果")
    private Map<String, List<VectorMatchResult>> vectorMatches;

    @Schema(description = "会话信息")
    private SessionInfo sessionInfo;

    /**
     * 线索匹配信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "线索匹配信息")
    public static class ClueMatchInfo {

        @Schema(description = "线索ID", example = "clue-123")
        private String clueId;

        @Schema(description = "线索内容", example = "凶手是一名30岁的男性")
        private String clueContent;

        @Schema(description = "线索类型", example = "CHARACTER")
        private String clueType;

        @Schema(description = "是否核心线索", example = "true")
        private Boolean isCoreClue;

        @Schema(description = "相似度", example = "0.85")
        private Double similarity;

        @Schema(description = "推理层级", example = "2")
        private Integer inferenceLevel;

        @Schema(description = "重要性评分", example = "8")
        private Integer importance;
    }

    /**
     * 向量匹配结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "向量匹配结果")
    public static class VectorMatchResult {

        @Schema(description = "向量类型", example = "CLUE")
        private String vectorType;

        @Schema(description = "Redis键名", example = "hai_gui:vec:clue:clue-123")
        private String redisKey;

        @Schema(description = "内容ID", example = "clue-123")
        private String contentId;

        @Schema(description = "匹配内容", example = "凶手是一名30岁的男性")
        private String content;

        @Schema(description = "相似度", example = "0.85")
        private Double similarity;
    }

    /**
     * 会话信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "会话信息")
    public static class SessionInfo {

        @Schema(description = "会话ID", example = "session-123")
        private String sessionId;

        @Schema(description = "海龟汤标题", example = "密室杀人案")
        private String soupTitle;

        @Schema(description = "当前进度", example = "0.65")
        private Double currentProgress;
    }

    /**
     * 创建成功响应
     */
    public static SoupQuestionResponse success(String soupId, String question, String answer, String explanation) {
        return SoupQuestionResponse.builder()
                .status("SUCCESS")
                .message("判断成功")
                .soupId(soupId)
                .question(question)
                .answer(answer)
                .explanation(explanation)
                .build();
    }

    /**
     * 创建失败响应
     */
    public static SoupQuestionResponse failure(String message) {
        return SoupQuestionResponse.builder()
                .status("FAILURE")
                .message(message)
                .build();
    }
}