package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.AiGenStatus;
import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import com.guanyu.haigui.pojo.result.HaiGuiInfoResult;
import com.guanyu.haigui.repository.HaiGuiSoupAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 审核 AI 生成异步执行器（按 auditId 加分布式锁，保证同一条审核同时只有一个 LLM 任务）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditAiGenerateService {

    private static final String LOCK_PREFIX = "audit:ai_generate:";
    private static final long LOCK_LEASE_MINUTES = 15;

    private final RedissonClient redissonClient;
    private final HaiGuiSoupAuditRepository haiGuiSoupAuditRepository;
    private final HaiGuiSoupInfoService haiGuiSoupInfoService;
    private final AuditDraftService auditDraftService;

    @Async("taskExecutor")
    public void generateAsync(Long auditId) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + auditId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, LOCK_LEASE_MINUTES, TimeUnit.MINUTES);
            if (!acquired) {
                log.warn("AI 生成锁未获取 auditId={}", auditId);
                return;
            }

            HaiGuiSoupAudit audit = haiGuiSoupAuditRepository.findById(auditId).orElse(null);
            if (audit == null) {
                log.warn("AI 生成时审核记录不存在 auditId={}", auditId);
                return;
            }
            if (audit.getAiGenStatus() != AiGenStatus.GENERATING) {
                log.info("跳过 AI 生成，状态非 GENERATING auditId={}, status={}",
                        auditId, audit.getAiGenStatus());
                return;
            }

            log.info("开始异步 AI 生成 auditId={}", auditId);
            String prompt = haiGuiSoupInfoService.generatePrompt(audit);
            HaiGuiInfoResult result = haiGuiSoupInfoService.generateInfo(prompt);
            auditDraftService.saveAiGenerationResult(auditId, result);
            log.info("异步 AI 生成成功 auditId={}, fragments={}, tasks={}",
                    auditId,
                    result.getFragments() != null ? result.getFragments().size() : 0,
                    result.getInferenceTasks() != null ? result.getInferenceTasks().size() : 0);
        } catch (Exception e) {
            log.error("异步 AI 生成失败 auditId={}", auditId, e);
            auditDraftService.markAiGenerationFailed(auditId, e.getMessage());
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
