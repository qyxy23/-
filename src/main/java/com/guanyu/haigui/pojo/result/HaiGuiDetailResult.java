package com.guanyu.haigui.pojo.result;

import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HaiGuiDetailResult {
    private Long auditId;

    private String title;

    private String surface;

    private String bottom;

    private Integer defaultMaxQuestions = 30;

    private Integer estimatedDuration = 30;

    private Integer playerCount = 0;

    private DifficultyLevel difficultyLevel = DifficultyLevel.BEGINNER;

    private SoupTag tags;

    private Long uploaderId;

    private HaiGuiSoupAudit.AuditStatus auditStatus = HaiGuiSoupAudit.AuditStatus.PENDING;


    private LocalDateTime createdAt;

    public static HaiGuiDetailResult fromHaiGuiSoupAudit(HaiGuiSoupAudit audit) {
        HaiGuiDetailResult result = new HaiGuiDetailResult();
        result.setAuditId(audit.getAuditId());
        result.setTitle(audit.getTitle());
        result.setSurface(audit.getSurface());
        result.setBottom(audit.getBottom());
        result.setDefaultMaxQuestions(audit.getDefaultMaxQuestions());
        result.setEstimatedDuration(audit.getEstimatedDuration());
        result.setPlayerCount(audit.getPlayerCount());
        result.setDifficultyLevel(audit.getDifficultyLevel());
        result.setTags(audit.getTags());
        result.setUploaderId(audit.getUploaderId());
        result.setAuditStatus(audit.getAuditStatus());
        result.setCreatedAt(audit.getCreatedAt());
        return result;
    }
}
