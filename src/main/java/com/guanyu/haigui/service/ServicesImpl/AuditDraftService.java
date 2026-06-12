package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.Enum.AiGenStatus;
import com.guanyu.haigui.Enum.PublishStatus;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.pojo.Info.ClueFragmentInfo;
import com.guanyu.haigui.pojo.Info.InferenceTaskInfo;
import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import com.guanyu.haigui.pojo.result.HaiGuiInfoResult;
import com.guanyu.haigui.repository.HaiGuiSoupAuditRepository;
import com.guanyu.haigui.service.AchievementService;
import com.guanyu.haigui.utils.HaiGuiInfoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审核草稿读写（与异步生成解耦，避免 AuditService 循环依赖）
 */
@Service
@RequiredArgsConstructor
public class AuditDraftService {

    private final HaiGuiSoupAuditRepository haiGuiSoupAuditRepository;
    private final ObjectMapper objectMapper;
    private final AchievementService achievementService;

    public void writeDraft(HaiGuiSoupAudit audit, String hostManual, String aiJudgeRules,
                           List<ClueFragmentInfo> fragments, List<InferenceTaskInfo> tasks) {
        try {
            audit.setDraftManual(HaiGuiInfoUtil.serializeDraftManual(hostManual, aiJudgeRules));
            String fragmentsStr = objectMapper.writeValueAsString(fragments);
            audit.setDraftFragments(objectMapper.readTree(fragmentsStr));
            String tasksStr = objectMapper.writeValueAsString(tasks);
            audit.setDraftTasks(objectMapper.readTree(tasksStr));
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "JSON序列化失败: " + e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveAiGenerationResult(Long auditId, HaiGuiInfoResult result) {
        HaiGuiSoupAudit audit = haiGuiSoupAuditRepository.findById(auditId)
                .orElseThrow(() -> new BusinessException(404, "审核记录不存在"));
        writeDraft(audit, result.getManual(), result.getAiJudgeRules(),
                result.getFragments(), result.getInferenceTasks());
        audit.setAiGenStatus(AiGenStatus.SUCCESS);
        audit.setAiGenError(null);
        audit.setAiGenUpdatedAt(LocalDateTime.now());
        haiGuiSoupAuditRepository.save(audit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markAiGenerationFailed(Long auditId, String error) {
        HaiGuiSoupAudit audit = haiGuiSoupAuditRepository.findById(auditId).orElse(null);
        if (audit == null) {
            return;
        }
        audit.setAiGenStatus(AiGenStatus.FAILED);
        audit.setAiGenError(error != null ? error : "AI 生成失败");
        audit.setAiGenUpdatedAt(LocalDateTime.now());
        haiGuiSoupAuditRepository.save(audit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markPublishSuccess(Long auditId, String soupId) {
        HaiGuiSoupAudit audit = haiGuiSoupAuditRepository.findById(auditId)
                .orElseThrow(() -> new BusinessException(404, "审核记录不存在"));
        audit.setOriginalSoupId(soupId);
        audit.setAuditStatus(HaiGuiSoupAudit.AuditStatus.APPROVED);
        audit.setAuditTime(LocalDateTime.now());
        audit.setPublishStatus(PublishStatus.SUCCESS);
        audit.setPublishError(null);
        audit.setPublishUpdatedAt(LocalDateTime.now());
        haiGuiSoupAuditRepository.save(audit);
        if (audit.getUploaderId() != null) {
            achievementService.onSoupPublishApproved(audit.getUploaderId(), soupId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markPublishFailed(Long auditId, String error) {
        HaiGuiSoupAudit audit = haiGuiSoupAuditRepository.findById(auditId).orElse(null);
        if (audit == null) {
            return;
        }
        audit.setPublishStatus(PublishStatus.FAILED);
        audit.setPublishError(error != null ? error : "发布失败");
        audit.setPublishUpdatedAt(LocalDateTime.now());
        haiGuiSoupAuditRepository.save(audit);
    }
}
