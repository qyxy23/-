package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.CoverAuditStatus;
import com.guanyu.haigui.Enum.ImageAuditVerdict;
import com.guanyu.haigui.Enum.UserRoleEnum;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.UploadHaiGuiSoupDTO;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import com.guanyu.haigui.pojo.model.SysUserRole;
import com.guanyu.haigui.pojo.vo.SoupCoverUploadVO;
import com.guanyu.haigui.repository.HaiGuiSoupAuditRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.SysUserRoleRepository;
import com.guanyu.haigui.utils.CiImageAuditService;
import com.guanyu.haigui.utils.CosUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 海龟汤服务实现类（重构版）
 * 集成向量化功能，提供智能搜索和推荐
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TurtleSoupService {

    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final CosUtil cosUtil;
    private final CiImageAuditService ciImageAuditService;
    private final HaiGuiSoupAuditRepository haiGuiSoupAuditRepository;
    private final SysUserRoleRepository sysUserRoleRepository;

    /**
     * 上传/更换海龟汤封面：审核员直传生效；上传者走机器审（疑似进人工）
     */
    public SoupCoverUploadVO uploadHaiGuiSoupAvatar(MultipartFile avatarFile, String soupId) {
        Long userId = BaseContext.getCurrentId();
        HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId)
                .orElseThrow(() -> new BusinessException(404, "故事不存在"));

        if (hasAuditPermission(userId)) {
            return uploadCoverAsAuditor(avatarFile, soup);
        }
        if (!isCoverUploader(userId, soup)) {
            throw new BusinessException(403, "无权上传此海龟汤封面");
        }
        return uploadCoverAsUploader(avatarFile, soup);
    }

    /** 封面上传者：正式表 uploaderId，或审核发布时误写审核员 id 的历史数据 */
    private boolean isCoverUploader(Long userId, HaiGuiSoup soup) {
        if (userId.equals(soup.getUploaderId())) {
            return true;
        }
        return haiGuiSoupAuditRepository.findFirstByOriginalSoupId(soup.getSoupId())
                .map(audit -> userId.equals(audit.getUploaderId()))
                .orElse(false);
    }

    public SoupCoverUploadVO approvePendingCover(String soupId) {
        Long userId = BaseContext.getCurrentId();
        if (!hasAuditPermission(userId)) {
            throw new BusinessException(403, "仅审核员可操作");
        }
        HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId)
                .orElseThrow(() -> new BusinessException(404, "故事不存在"));
        if (soup.getCoverAuditStatus() != CoverAuditStatus.PENDING_REVIEW
                || !StringUtils.hasText(soup.getPendingCoverUrl())) {
            throw new BusinessException(400, "当前没有待复核的封面");
        }

        String oldAvatar = soup.getSoupAvatar();
        String pendingUrl = soup.getPendingCoverUrl();
        soup.setSoupAvatar(pendingUrl);
        soup.setPendingCoverUrl(null);
        soup.setCoverAuditStatus(CoverAuditStatus.NONE);
        haiGuiSoupRepository.save(soup);

        if (StringUtils.hasText(oldAvatar) && !oldAvatar.equals(pendingUrl)) {
            cosUtil.deleteByUrl(oldAvatar);
        }
        log.info("封面人工通过 → soupId={}, url={}", soupId, pendingUrl);
        return buildCoverVo(soup, "封面已通过人工复核");
    }

    public SoupCoverUploadVO rejectPendingCover(String soupId, String reason) {
        Long userId = BaseContext.getCurrentId();
        if (!hasAuditPermission(userId)) {
            throw new BusinessException(403, "仅审核员可操作");
        }
        HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId)
                .orElseThrow(() -> new BusinessException(404, "故事不存在"));
        if (soup.getCoverAuditStatus() != CoverAuditStatus.PENDING_REVIEW
                || !StringUtils.hasText(soup.getPendingCoverUrl())) {
            throw new BusinessException(400, "当前没有待复核的封面");
        }

        cosUtil.deleteByUrl(soup.getPendingCoverUrl());
        soup.setPendingCoverUrl(null);
        soup.setCoverAuditStatus(CoverAuditStatus.REJECTED);
        haiGuiSoupRepository.save(soup);

        String msg = StringUtils.hasText(reason) ? reason : "封面未通过人工复核";
        log.info("封面人工拒绝 → soupId={}, reason={}", soupId, msg);
        return buildCoverVo(soup, msg);
    }

    private SoupCoverUploadVO uploadCoverAsAuditor(MultipartFile avatarFile, HaiGuiSoup soup) {
        CosUtil.UploadedImage uploaded = cosUtil.uploadSoupCoverOfficial(avatarFile, soup.getSoupId());
        replaceOfficialCover(soup, uploaded.url());
        clearPendingCover(soup);
        haiGuiSoupRepository.save(soup);
        log.info("审核员更新封面 → soupId={}, url={}", soup.getSoupId(), uploaded.url());
        return buildCoverVo(soup, "封面已更新");
    }

    private SoupCoverUploadVO uploadCoverAsUploader(MultipartFile avatarFile, HaiGuiSoup soup) {
        clearPendingCover(soup);

        CosUtil.UploadedImage uploaded = cosUtil.uploadSoupCoverPending(avatarFile, soup.getSoupId());
        ImageAuditVerdict verdict = ciImageAuditService.auditCover(uploaded.objectKey(), uploaded.sizeBytes());

        if (verdict == ImageAuditVerdict.REJECT) {
            cosUtil.deleteByUrl(uploaded.url());
            soup.setCoverAuditStatus(CoverAuditStatus.REJECTED);
            haiGuiSoupRepository.save(soup);
            throw new BusinessException(400, "封面含有违规内容，请更换图片");
        }

        if (verdict == ImageAuditVerdict.SUSPECT) {
            soup.setPendingCoverUrl(uploaded.url());
            soup.setCoverAuditStatus(CoverAuditStatus.PENDING_REVIEW);
            haiGuiSoupRepository.save(soup);
            log.info("上传者封面待人工复核 → soupId={}, url={}", soup.getSoupId(), uploaded.url());
            return buildCoverVo(soup, "封面已提交，待人工复核后展示");
        }

        replaceOfficialCover(soup, uploaded.url());
        soup.setCoverAuditStatus(CoverAuditStatus.NONE);
        haiGuiSoupRepository.save(soup);
        log.info("上传者封面机器审通过 → soupId={}, url={}", soup.getSoupId(), uploaded.url());
        return buildCoverVo(soup, "封面已更新");
    }

    private void replaceOfficialCover(HaiGuiSoup soup, String newUrl) {
        if (StringUtils.hasText(soup.getSoupAvatar()) && !soup.getSoupAvatar().equals(newUrl)) {
            cosUtil.deleteByUrl(soup.getSoupAvatar());
        }
        soup.setSoupAvatar(newUrl);
    }

    private void clearPendingCover(HaiGuiSoup soup) {
        if (StringUtils.hasText(soup.getPendingCoverUrl())) {
            cosUtil.deleteByUrl(soup.getPendingCoverUrl());
            soup.setPendingCoverUrl(null);
        }
        if (soup.getCoverAuditStatus() == CoverAuditStatus.PENDING_REVIEW) {
            soup.setCoverAuditStatus(CoverAuditStatus.NONE);
        }
    }

    private SoupCoverUploadVO buildCoverVo(HaiGuiSoup soup, String message) {
        return SoupCoverUploadVO.builder()
                .avatarUrl(StringUtils.hasText(soup.getSoupAvatar()) ? soup.getSoupAvatar() : "")
                .pendingCoverUrl(soup.getPendingCoverUrl())
                .coverAuditStatus(soup.getCoverAuditStatus())
                .message(message)
                .build();
    }

    public String uploadTurtleSoup(UploadHaiGuiSoupDTO soup) {
        HaiGuiSoupAudit audit = UploadHaiGuiSoupDTO.from(soup);
        haiGuiSoupAuditRepository.save(audit);
        return "上传成功,请等待审核";
    }

    private boolean hasAuditPermission(Long userId) {
        return sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(userId, UserRoleEnum.SOUP_AUDITOR.getRoleId()))
                || sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(userId, UserRoleEnum.ADMIN.getRoleId()));
    }
}
