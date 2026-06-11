package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.CoverAuditStatus;
import com.guanyu.haigui.Enum.CoverReportReason;
import com.guanyu.haigui.Enum.CoverReportStatus;
import com.guanyu.haigui.Enum.UserRoleEnum;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.HandleCoverReportDTO;
import com.guanyu.haigui.pojo.dto.QueryCoverReportListDTO;
import com.guanyu.haigui.pojo.dto.SubmitCoverReportDTO;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.SoupCoverReport;
import com.guanyu.haigui.pojo.model.SysUserRole;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.CoverReportItemVO;
import com.guanyu.haigui.pojo.vo.CoverReportListVO;
import com.guanyu.haigui.repository.HaiGuiSoupAuditRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.SoupCoverReportRepository;
import com.guanyu.haigui.repository.SysUserRoleRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.utils.CosUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SoupCoverReportService {

    private static final String ACTION_DISMISS = "DISMISS";
    private static final String ACTION_REMOVE_COVER = "REMOVE_COVER";

    private final SoupCoverReportRepository soupCoverReportRepository;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final HaiGuiSoupAuditRepository haiGuiSoupAuditRepository;
    private final UserInfoRepository userInfoRepository;
    private final SysUserRoleRepository sysUserRoleRepository;
    private final CosUtil cosUtil;

    public CoverReportItemVO submitReport(SubmitCoverReportDTO dto) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (dto == null || !StringUtils.hasText(dto.getSoupId())) {
            throw new BusinessException(400, "海龟汤信息无效");
        }
        if (dto.getReasonType() == null) {
            throw new BusinessException(400, "请选择举报原因");
        }
        if (dto.getReasonType() == CoverReportReason.OTHER
                && !StringUtils.hasText(dto.getReasonDetail())) {
            throw new BusinessException(400, "请填写举报说明");
        }

        HaiGuiSoup soup = haiGuiSoupRepository.findById(dto.getSoupId().trim())
                .orElseThrow(() -> new BusinessException(404, "故事不存在"));
        if (!StringUtils.hasText(soup.getSoupAvatar())) {
            throw new BusinessException(400, "该海龟汤暂无封面");
        }
        if (isCoverOwner(userId, soup)) {
            throw new BusinessException(400, "无法举报自己的封面");
        }

        String coverUrl = soup.getSoupAvatar();
        if (soupCoverReportRepository.existsByReporterIdAndSoupIdAndCoverUrlAndStatus(
                userId, soup.getSoupId(), coverUrl, CoverReportStatus.PENDING)) {
            throw new BusinessException(400, "您已举报过该封面，请等待处理");
        }

        SoupCoverReport report = new SoupCoverReport();
        report.setSoupId(soup.getSoupId());
        report.setReporterId(userId);
        report.setCoverUrl(coverUrl);
        report.setReasonType(dto.getReasonType());
        report.setReasonDetail(StringUtils.hasText(dto.getReasonDetail())
                ? dto.getReasonDetail().trim() : null);
        report.setStatus(CoverReportStatus.PENDING);
        soupCoverReportRepository.save(report);

        log.info("封面举报提交 → reportId={}, soupId={}, reporterId={}",
                report.getReportId(), soup.getSoupId(), userId);
        return toItemVo(report, soup.getSoupTitle(), resolveUsername(userId));
    }

    public CoverReportListVO listReports(QueryCoverReportListDTO dto) {
        requireAuditPermission();
        int pageNum = dto != null && dto.getPageNum() != null && dto.getPageNum() > 0
                ? dto.getPageNum() : 1;
        int pageSize = dto != null && dto.getPageSize() != null && dto.getPageSize() > 0
                ? Math.min(dto.getPageSize(), 50) : 10;
        PageRequest pageable = PageRequest.of(pageNum - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<SoupCoverReport> page;
        if (dto != null && dto.getStatus() != null) {
            page = soupCoverReportRepository.findByStatus(dto.getStatus(), pageable);
        } else {
            page = soupCoverReportRepository.findByStatusNot(CoverReportStatus.CANCELLED, pageable);
        }

        Set<String> soupIds = page.getContent().stream()
                .map(SoupCoverReport::getSoupId)
                .collect(Collectors.toSet());
        Map<String, String> titleMap = haiGuiSoupRepository.findAllById(soupIds).stream()
                .collect(Collectors.toMap(HaiGuiSoup::getSoupId, HaiGuiSoup::getSoupTitle));

        Set<Long> reporterIds = page.getContent().stream()
                .map(SoupCoverReport::getReporterId)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = userInfoRepository.findAllById(reporterIds).stream()
                .collect(Collectors.toMap(UserInfo::getUserId, UserInfo::getUsername));

        List<CoverReportItemVO> list = page.getContent().stream()
                .map(r -> toItemVo(r, titleMap.get(r.getSoupId()), nameMap.get(r.getReporterId())))
                .toList();

        return CoverReportListVO.builder()
                .list(list)
                .total(page.getTotalElements())
                .pages(page.getTotalPages())
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();
    }

    public CoverReportItemVO handleReport(HandleCoverReportDTO dto) {
        Long handlerId = requireAuditPermission();
        if (dto == null || dto.getReportId() == null) {
            throw new BusinessException(400, "举报信息无效");
        }
        if (!StringUtils.hasText(dto.getAction())) {
            throw new BusinessException(400, "请选择处理方式");
        }

        SoupCoverReport report = soupCoverReportRepository.findById(dto.getReportId())
                .orElseThrow(() -> new BusinessException(404, "举报记录不存在"));
        if (report.getStatus() != CoverReportStatus.PENDING) {
            throw new BusinessException(400, "该举报已处理");
        }

        String action = dto.getAction().trim().toUpperCase();
        String note = StringUtils.hasText(dto.getHandleNote()) ? dto.getHandleNote().trim() : null;
        LocalDateTime now = LocalDateTime.now();

        if (ACTION_DISMISS.equals(action)) {
            report.setStatus(CoverReportStatus.DISMISSED);
            report.setHandlerId(handlerId);
            report.setHandleNote(note);
            report.setHandledAt(now);
            soupCoverReportRepository.save(report);
            log.info("封面举报驳回 → reportId={}, handlerId={}", report.getReportId(), handlerId);
        } else if (ACTION_REMOVE_COVER.equals(action)) {
            HaiGuiSoup soup = haiGuiSoupRepository.findById(report.getSoupId())
                    .orElseThrow(() -> new BusinessException(404, "故事不存在"));
            removeCover(soup, report.getCoverUrl());

            List<SoupCoverReport> related = soupCoverReportRepository
                    .findBySoupIdAndCoverUrlAndStatus(
                            report.getSoupId(), report.getCoverUrl(), CoverReportStatus.PENDING);
            for (SoupCoverReport pending : related) {
                pending.setStatus(CoverReportStatus.COVER_REMOVED);
                pending.setHandlerId(handlerId);
                pending.setHandleNote(note);
                pending.setHandledAt(now);
            }
            soupCoverReportRepository.saveAll(related);
            log.info("封面举报下架 → reportId={}, soupId={}, handlerId={}",
                    report.getReportId(), report.getSoupId(), handlerId);
        } else {
            throw new BusinessException(400, "无效的处理方式");
        }

        HaiGuiSoup soup = haiGuiSoupRepository.findById(report.getSoupId()).orElse(null);
        String title = soup != null ? soup.getSoupTitle() : "";
        return toItemVo(report, title, resolveUsername(report.getReporterId()));
    }

    public CoverReportListVO listMyReports(QueryCoverReportListDTO dto) {
        Long userId = requireLoginUser();
        int pageNum = dto != null && dto.getPageNum() != null && dto.getPageNum() > 0
                ? dto.getPageNum() : 1;
        int pageSize = dto != null && dto.getPageSize() != null && dto.getPageSize() > 0
                ? Math.min(dto.getPageSize(), 50) : 10;
        PageRequest pageable = PageRequest.of(pageNum - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<SoupCoverReport> page = soupCoverReportRepository
                .findByReporterIdOrderByCreatedAtDesc(userId, pageable);

        Set<String> soupIds = page.getContent().stream()
                .map(SoupCoverReport::getSoupId)
                .collect(Collectors.toSet());
        Map<String, String> titleMap = haiGuiSoupRepository.findAllById(soupIds).stream()
                .collect(Collectors.toMap(HaiGuiSoup::getSoupId, HaiGuiSoup::getSoupTitle));

        List<CoverReportItemVO> list = page.getContent().stream()
                .map(r -> toItemVo(r, titleMap.get(r.getSoupId()), resolveUsername(userId)))
                .toList();

        return CoverReportListVO.builder()
                .list(list)
                .total(page.getTotalElements())
                .pages(page.getTotalPages())
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();
    }

    public CoverReportItemVO withdrawMyReport(Long reportId) {
        Long userId = requireLoginUser();
        SoupCoverReport report = soupCoverReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(404, "举报记录不存在"));
        if (!userId.equals(report.getReporterId())) {
            throw new BusinessException(403, "无权操作此举报");
        }
        if (report.getStatus() != CoverReportStatus.PENDING) {
            throw new BusinessException(400, "仅待处理的举报可撤回");
        }
        report.setStatus(CoverReportStatus.CANCELLED);
        report.setHandledAt(LocalDateTime.now());
        soupCoverReportRepository.save(report);
        log.info("封面举报撤回 → reportId={}, reporterId={}", reportId, userId);

        HaiGuiSoup soup = haiGuiSoupRepository.findById(report.getSoupId()).orElse(null);
        String title = soup != null ? soup.getSoupTitle() : "";
        return toItemVo(report, title, resolveUsername(userId));
    }

    private void removeCover(HaiGuiSoup soup, String reportedCoverUrl) {
        if (StringUtils.hasText(soup.getSoupAvatar())
                && Objects.equals(soup.getSoupAvatar(), reportedCoverUrl)) {
            cosUtil.deleteByUrl(soup.getSoupAvatar());
            soup.setSoupAvatar("");
        }
        if (StringUtils.hasText(soup.getPendingCoverUrl())) {
            cosUtil.deleteByUrl(soup.getPendingCoverUrl());
            soup.setPendingCoverUrl(null);
        }
        soup.setCoverAuditStatus(CoverAuditStatus.NONE);
        haiGuiSoupRepository.save(soup);
    }

    private boolean isCoverOwner(Long userId, HaiGuiSoup soup) {
        if (userId.equals(soup.getUploaderId())) {
            return true;
        }
        return haiGuiSoupAuditRepository.findFirstByOriginalSoupId(soup.getSoupId())
                .map(audit -> userId.equals(audit.getUploaderId()))
                .orElse(false);
    }

    private Long requireAuditPermission() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (!hasAuditPermission(userId)) {
            throw new BusinessException(403, "仅审核员可操作");
        }
        return userId;
    }

    private Long requireLoginUser() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }

    private boolean hasAuditPermission(Long userId) {
        return sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(userId, UserRoleEnum.SOUP_AUDITOR.getRoleId()))
                || sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(userId, UserRoleEnum.ADMIN.getRoleId()));
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return "";
        }
        return userInfoRepository.findById(userId)
                .map(UserInfo::getUsername)
                .orElse("");
    }

    private CoverReportItemVO toItemVo(SoupCoverReport report, String soupTitle, String reporterName) {
        return CoverReportItemVO.builder()
                .reportId(report.getReportId())
                .soupId(report.getSoupId())
                .soupTitle(soupTitle != null ? soupTitle : "")
                .coverUrl(report.getCoverUrl())
                .reporterId(report.getReporterId())
                .reporterName(reporterName != null ? reporterName : "")
                .reasonType(report.getReasonType())
                .reasonTypeLabel(report.getReasonType() != null
                        ? report.getReasonType().getLabel() : "")
                .reasonDetail(report.getReasonDetail())
                .status(report.getStatus())
                .statusLabel(statusLabel(report.getStatus()))
                .handleNote(report.getHandleNote())
                .createdAt(report.getCreatedAt())
                .handledAt(report.getHandledAt())
                .build();
    }

    private static String statusLabel(CoverReportStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case PENDING -> "待处理";
            case DISMISSED -> "已驳回";
            case COVER_REMOVED -> "已下架封面";
            case CANCELLED -> "已撤回";
        };
    }
}
