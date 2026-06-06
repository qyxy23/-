package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.PublishStatus;
import com.guanyu.haigui.pojo.dto.CreateTurtleSoupDTO;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import com.guanyu.haigui.pojo.model.InferenceTask;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.result.HaiGuiInfoResult;
import com.guanyu.haigui.repository.HaiGuiSoupAuditRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.utils.AuditDraftValidator;
import com.guanyu.haigui.utils.HaiGuiInfoUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 审核通过发布异步执行器（按 auditId 加锁，向量化入库在后台完成）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditPublishService {

    private static final String LOCK_PREFIX = "audit:publish:";
    private static final long LOCK_LEASE_MINUTES = 30;

    private final RedissonClient redissonClient;
    private final HaiGuiSoupAuditRepository haiGuiSoupAuditRepository;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final UserInfoRepository userInfoRepository;
    private final HaiGuiSoupInfoService haiGuiSoupInfoService;
    private final AuditDraftService auditDraftService;
    private final RedisStackClient redisStackClient;

    @Async("taskExecutor")
    public void publishAsync(Long auditId) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + auditId);
        boolean acquired = false;
        String createdSoupId = null;
        try {
            acquired = lock.tryLock(0, LOCK_LEASE_MINUTES, TimeUnit.MINUTES);
            if (!acquired) {
                log.warn("发布锁未获取 auditId={}", auditId);
                return;
            }

            HaiGuiSoupAudit audit = haiGuiSoupAuditRepository.findById(auditId).orElse(null);
            if (audit == null) {
                log.warn("发布时审核记录不存在 auditId={}", auditId);
                return;
            }
            if (audit.getPublishStatus() != PublishStatus.PUBLISHING) {
                log.info("跳过发布，状态非 PUBLISHING auditId={}, status={}",
                        auditId, audit.getPublishStatus());
                return;
            }
            if (audit.getAuditorId() == null) {
                auditDraftService.markPublishFailed(auditId, "缺少审核员信息");
                return;
            }

            UserInfo auditor = userInfoRepository.findById(audit.getAuditorId()).orElse(null);
            if (auditor == null) {
                auditDraftService.markPublishFailed(auditId, "审核员不存在");
                return;
            }

            CreateTurtleSoupDTO dto = buildPublishDto(audit);
            AuditDraftValidator.validateTaskPrerequisites(dto.getFragments(), dto.getInferenceTasks());

            log.info("开始异步发布 auditId={}, fragments={}, tasks={}",
                    auditId,
                    dto.getFragments() != null ? dto.getFragments().size() : 0,
                    dto.getInferenceTasks() != null ? dto.getInferenceTasks().size() : 0);

            HaiGuiSoup soup = dto.fromToHaiGuiSoup(auditor);
            createdSoupId = soup.getSoupId();
            haiGuiSoupRepository.save(soup);

            Map<Integer, Long> fragmentMap = haiGuiSoupInfoService.convertToClueFragmentsAndSave(
                    dto.getFragments(), soup);
            List<InferenceTask> tasks = haiGuiSoupInfoService.convertToInferenceTasks(
                    dto.getInferenceTasks(), soup, fragmentMap);
            if (fragmentMap.isEmpty() || tasks.isEmpty()) {
                throw new IllegalStateException("线索或推理任务入库失败，请检查数据");
            }

            auditDraftService.markPublishSuccess(auditId, soup.getSoupId());
            log.info("异步发布成功 auditId={}, soupId={}", auditId, soup.getSoupId());
        } catch (Exception e) {
            log.error("异步发布失败 auditId={}", auditId, e);
            if (createdSoupId != null) {
                try {
                    redisStackClient.deleteSoup(createdSoupId);
                    haiGuiSoupRepository.deleteById(createdSoupId);
                } catch (Exception cleanupEx) {
                    log.warn("发布失败后清理 soup 异常 auditId={}, soupId={}",
                            auditId, createdSoupId, cleanupEx);
                }
            }
            String msg = e.getMessage() != null ? e.getMessage() : "发布失败";
            auditDraftService.markPublishFailed(auditId, msg);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private CreateTurtleSoupDTO buildPublishDto(HaiGuiSoupAudit audit) {
        HaiGuiInfoResult info = haiGuiSoupInfoService.getFragmentsAndTasks(
                audit.getDraftFragments(), audit.getDraftTasks());
        HaiGuiInfoUtil.DraftManualContent draftManual =
                HaiGuiInfoUtil.parseDraftManual(audit.getDraftManual());
        CreateTurtleSoupDTO dto = new CreateTurtleSoupDTO();
        dto.setAuditRecordId(audit.getAuditId());
        dto.setSoupTitle(audit.getTitle());
        dto.setSoupSurface(audit.getSurface());
        dto.setSoupBottom(audit.getBottom());
        dto.setManual(firstNonBlank(info.getManual(), draftManual.getHostManual()));
        dto.setAiJudgeRules(firstNonBlank(info.getAiJudgeRules(), draftManual.getAiJudgeRules()));
        dto.setFragments(info.getFragments());
        dto.setInferenceTasks(info.getInferenceTasks());
        dto.setEstimatedDuration(audit.getEstimatedDuration());
        dto.setPlayerCount(audit.getPlayerCount());
        dto.setDifficultyLevel(audit.getDifficultyLevel());
        dto.setTag(audit.getTags());
        dto.setMaxRounds(audit.getDefaultMaxQuestions());
        return dto;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary;
        }
        return fallback != null ? fallback : "";
    }
}
