package com.guanyu.haigui.service;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.http.HttpUtil;
import com.guanyu.haigui.Enum.CoverAuditStatus;
import com.guanyu.haigui.Enum.ImageAuditVerdict;
import com.guanyu.haigui.Enum.UserRoleEnum;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.client.AiImageGenerateClient;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.SysUserRole;
import com.guanyu.haigui.pojo.vo.CoverAiGenStatusVO;
import com.guanyu.haigui.pojo.vo.SoupCoverUploadVO;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.SysUserRoleRepository;
import com.guanyu.haigui.utils.CiImageAuditService;
import com.guanyu.haigui.utils.CosUtil;
import com.guanyu.haigui.utils.SoupCoverPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

/**
 * 审核员 AI 生成海龟汤封面：文生图 → 机器审 → 直接替换正式封面
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoupCoverAiService {

    private final AiImageGenerateClient aiImageGenerateClient;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final SysUserRoleRepository sysUserRoleRepository;
    private final CosUtil cosUtil;
    private final CiImageAuditService ciImageAuditService;
    private final SoupCoverAiGenStatusService coverAiGenStatusService;

    public CoverAiGenStatusVO getGenerateStatus(String soupId) {
        Long userId = BaseContext.getCurrentId();
        if (!hasAuditPermission(userId)) {
            throw new BusinessException(403, "仅审核员可查看");
        }
        haiGuiSoupRepository.findById(soupId)
                .orElseThrow(() -> new BusinessException(404, "故事不存在"));
        return coverAiGenStatusService.resolveStatus(soupId, userId);
    }

    public SoupCoverUploadVO generateCover(String soupId) {
        Long userId = BaseContext.getCurrentId();
        if (!hasAuditPermission(userId)) {
            throw new BusinessException(403, "仅审核员可使用 AI 生成封面");
        }

        haiGuiSoupRepository.findById(soupId)
                .orElseThrow(() -> new BusinessException(404, "故事不存在"));

        RLock lock = coverAiGenStatusService.getLock(soupId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, SoupCoverAiGenStatusService.LOCK_LEASE_MINUTES, TimeUnit.MINUTES);
            if (!acquired) {
                throw new BusinessException(409, "其他审核员正在为该海龟汤生成封面，请稍后再试");
            }
            coverAiGenStatusService.markGenerating(soupId, userId);

            HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId)
                    .orElseThrow(() -> new BusinessException(404, "故事不存在"));
            assertNoUploaderPending(soup);

            String prompt = SoupCoverPromptBuilder.build(soup);
            log.info("AI 封面生成开始 soupId={}, userId={}, promptLen={}", soupId, userId, prompt.length());

            String tempUrl = aiImageGenerateClient.generateImageUrl(prompt);
            byte[] bytes = HttpUtil.downloadBytes(tempUrl);
            if (bytes == null || bytes.length == 0) {
                throw new BusinessException(502, "下载生成图片失败");
            }
            bytes = scaleToCoverOutput(bytes);
            if (bytes.length > 10 * 1024 * 1024) {
                throw new BusinessException(400, "生成图片过大");
            }

            CosUtil.UploadedImage uploaded = cosUtil.uploadSoupCoverOfficialBytes(soupId, bytes, "image/jpeg");
            ImageAuditVerdict verdict = ciImageAuditService.auditCover(uploaded.objectKey(), uploaded.sizeBytes());
            if (verdict != ImageAuditVerdict.PASS) {
                cosUtil.deleteByUrl(uploaded.url());
                throw new BusinessException(400, verdict == ImageAuditVerdict.REJECT
                        ? "AI 封面含有违规内容，请调整汤面信息后重试或手动上传"
                        : "AI 封面未通过安全审核，请手动上传");
            }

            HaiGuiSoup saved = applyOfficialCover(soupId, uploaded.url());

            log.info("AI 封面已应用 soupId={}, url={}", soupId, uploaded.url());
            return buildVo(saved, "AI 封面已更新", prompt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "封面生成被中断");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 封面生成失败 soupId={}", soupId, e);
            throw new BusinessException(502, "AI 封面生成失败：" + e.getMessage());
        } finally {
            coverAiGenStatusService.clearGenerating(soupId);
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private HaiGuiSoup applyOfficialCover(String soupId, String newUrl) {
        HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId)
                .orElseThrow(() -> new BusinessException(404, "故事不存在"));
        if (StringUtils.hasText(soup.getSoupAvatar()) && !soup.getSoupAvatar().equals(newUrl)) {
            cosUtil.deleteByUrl(soup.getSoupAvatar());
        }
        soup.setSoupAvatar(newUrl);
        if (soup.getCoverAuditStatus() == CoverAuditStatus.AI_DRAFT) {
            if (StringUtils.hasText(soup.getPendingCoverUrl()) && !soup.getPendingCoverUrl().equals(newUrl)) {
                cosUtil.deleteByUrl(soup.getPendingCoverUrl());
            }
            soup.setPendingCoverUrl(null);
            soup.setCoverAuditStatus(CoverAuditStatus.NONE);
        }
        return haiGuiSoupRepository.save(soup);
    }

    private void assertNoUploaderPending(HaiGuiSoup soup) {
        if (soup.getCoverAuditStatus() == CoverAuditStatus.PENDING_REVIEW
                && StringUtils.hasText(soup.getPendingCoverUrl())) {
            throw new BusinessException(400, "存在上传者待复核封面，请先处理后再 AI 生成");
        }
    }

    private SoupCoverUploadVO buildVo(HaiGuiSoup soup, String message, String prompt) {
        return SoupCoverUploadVO.builder()
                .avatarUrl(StringUtils.hasText(soup.getSoupAvatar()) ? soup.getSoupAvatar() : "")
                .pendingCoverUrl(soup.getPendingCoverUrl())
                .coverAuditStatus(soup.getCoverAuditStatus())
                .message(message)
                .promptUsed(prompt)
                .build();
    }

    private boolean hasAuditPermission(Long userId) {
        return sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(userId, UserRoleEnum.SOUP_AUDITOR.getRoleId()))
                || sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(userId, UserRoleEnum.ADMIN.getRoleId()));
    }

    private byte[] scaleToCoverOutput(byte[] raw) {
        int targetW = SoupCoverPromptBuilder.COVER_WIDTH;
        int targetH = SoupCoverPromptBuilder.COVER_HEIGHT;
        try {
            BufferedImage src = ImgUtil.read(new ByteArrayInputStream(raw));
            if (src == null) {
                return raw;
            }
            if (src.getWidth() == targetW && src.getHeight() == targetH) {
                return raw;
            }
            Image scaled = ImgUtil.scale(src, targetW, targetH);
            return ImgUtil.toBytes(ImgUtil.toBufferedImage(scaled), "jpg");
        } catch (Exception e) {
            log.warn("AI 封面缩放至 {}x{} 失败，使用原图", targetW, targetH, e);
            return raw;
        }
    }
}
