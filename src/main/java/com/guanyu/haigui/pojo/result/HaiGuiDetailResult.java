package com.guanyu.haigui.pojo.result;

import com.guanyu.haigui.Enum.AiGenStatus;
import com.guanyu.haigui.Enum.PublishStatus;
import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import com.guanyu.haigui.pojo.Info.ClueFragmentInfo;
import com.guanyu.haigui.pojo.Info.InferenceTaskInfo;
import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import com.guanyu.haigui.utils.HaiGuiInfoUtil;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class HaiGuiDetailResult{
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

    /** 拒绝原因（已拒绝时有值） */
    private String rejectReason;

    private LocalDateTime createdAt;

    /** 主持人手册（真人主持参考） */
    private String manual;
    /** AI 判题规则 */
    private String aiJudgeRules;
    private List<ClueFragmentInfo> fragments;
    private List<InferenceTaskInfo> inferenceTasks;

    private AiGenStatus aiGenStatus = AiGenStatus.IDLE;
    private String aiGenError;
    private LocalDateTime aiGenUpdatedAt;

    private PublishStatus publishStatus = PublishStatus.IDLE;
    private String publishError;
    private LocalDateTime publishUpdatedAt;

    /** 发布后对应的海龟汤 ID（审核通过后有值） */
    private String originalSoupId;

    /** 海龟汤封面图 URL */
    private String soupAvatar;

    public static HaiGuiDetailResult fromHaiGuiSoupAudit(HaiGuiSoupAudit audit, HaiGuiInfoResult haiGuiInfoResult) {
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
        result.setRejectReason(audit.getAuditComment());
        result.setCreatedAt(audit.getCreatedAt());

        HaiGuiInfoUtil.DraftManualContent draftManual = HaiGuiInfoUtil.parseDraftManual(audit.getDraftManual());
        result.setManual(firstNonBlank(haiGuiInfoResult.getManual(), draftManual.getHostManual()));
        result.setAiJudgeRules(firstNonBlank(haiGuiInfoResult.getAiJudgeRules(), draftManual.getAiJudgeRules()));
        result.setFragments(haiGuiInfoResult.getFragments());
        result.setInferenceTasks(haiGuiInfoResult.getInferenceTasks());
        result.setAiGenStatus(audit.getAiGenStatus() != null ? audit.getAiGenStatus() : AiGenStatus.IDLE);
        result.setAiGenError(audit.getAiGenError());
        result.setAiGenUpdatedAt(audit.getAiGenUpdatedAt());
        result.setPublishStatus(audit.getPublishStatus() != null ? audit.getPublishStatus() : PublishStatus.IDLE);
        result.setPublishError(audit.getPublishError());
        result.setPublishUpdatedAt(audit.getPublishUpdatedAt());
        result.setOriginalSoupId(audit.getOriginalSoupId());
        return result;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback != null ? fallback : "";
    }
}
