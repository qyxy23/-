package com.guanyu.haigui.pojo.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import lombok.Data;

import java.util.List;

@Data
public class UploadHaiGuiSoupDTO {
    // 标题
    private String soupTitle;

    // 汤
    private String soupSurface;

    // 底
    private String soupBottom;

    // 预计游玩时间（分钟）
    private Integer estimatedDuration = 30;

    // 游玩人数限制，0表示不限制，最多10人
    private Integer playerCount = 0;

    // 难度等级
    private DifficultyLevel difficultyLevel = DifficultyLevel.BEGINNER;

    // 海龟汤标签（只能选择一个）
    private SoupTag tag;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static HaiGuiSoupAudit from(UploadHaiGuiSoupDTO soup) {
        HaiGuiSoupAudit audit = new HaiGuiSoupAudit();
        audit.setTitle(soup.getSoupTitle());
        audit.setSurface(soup.getSoupSurface());
        audit.setBottom(soup.getSoupBottom());
        audit.setDefaultMaxQuestions(soup.getEstimatedDuration());
        audit.setEstimatedDuration(soup.getEstimatedDuration());
        audit.setPlayerCount(soup.getPlayerCount());
        audit.setDifficultyLevel(soup.getDifficultyLevel());
        audit.setTags(soup.getTagsAsString());
        audit.setUploaderId(BaseContext.getCurrentId());
        audit.setAuditStatus(HaiGuiSoupAudit.AuditStatus.PENDING);
        return audit;
    }


    /**
     * 获取标签字符串（单个标签）
     */
    public String getTagsAsString() {
        if (tag == null) {
            return "[]";
        }
        try {
            // 将单个标签转换为描述
            String tagDescription = tag.getDescription();
            return objectMapper.writeValueAsString(List.of(tagDescription));
        } catch (JsonProcessingException e) {
            // 如果序列化失败，返回标签描述
            return "[" + tag.getDescription() + "]";
        }
    }
}
