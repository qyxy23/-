package com.guanyu.haigui.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 海龟汤问题请求DTO
 * 用于接收海龟汤ID和问题，进行向量化匹配和AI判断
 */
@Data
@Schema(description = "海龟汤问题请求")
public class SoupQuestionRequest {
    @Schema(description = "海龟汤ID", example = "uuid-haigui-soup-id")
    private String soupId;

    @Schema(description = "玩家问题", example = "凶手是男性吗？")
    private String question;

    @Schema(description = "返回相关上下文数量", defaultValue = "5", example = "5")
    private Integer topK = 5;

    @Schema(description = "是否包含匹配详情", defaultValue = "false", example = "false")
    private Boolean includeMatchDetails = false;

    @Schema(description = "最小匹配阈值", defaultValue = "0.3", example = "0.3")
    private Double minSimilarity = 0.3;

    @Schema(description = "用户ID（可选，用于统计）", example = "12345")
    private Long userId;
}