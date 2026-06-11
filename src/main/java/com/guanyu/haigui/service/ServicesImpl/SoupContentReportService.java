package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.CoverReportReason;
import com.guanyu.haigui.Enum.SoupContentReportStatus;
import com.guanyu.haigui.Enum.UserRoleEnum;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.HandleSoupContentReportDTO;
import com.guanyu.haigui.pojo.dto.QuerySoupReportListDTO;
import com.guanyu.haigui.pojo.dto.SubmitSoupContentReportDTO;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.SoupContentReport;
import com.guanyu.haigui.pojo.model.SysUserRole;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.SoupContentReportItemVO;
import com.guanyu.haigui.pojo.vo.SoupContentReportListVO;
import com.guanyu.haigui.repository.HaiGuiSoupAuditRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.SoupContentReportRepository;
import com.guanyu.haigui.repository.SysUserRoleRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SoupContentReportService {

    private static final String ACTION_DISMISS = "DISMISS";
    private static final String ACTION_TAKE_DOWN = "TAKE_DOWN";

    private final SoupContentReportRepository soupContentReportRepository;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final HaiGuiSoupAuditRepository haiGuiSoupAuditRepository;
    private final UserInfoRepository userInfoRepository;
    private final SysUserRoleRepository sysUserRoleRepository;
    private final SoupTakedownService soupTakedownService;

    public SoupContentReportItemVO submitReport(SubmitSoupContentReportDTO dto) {
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
        if (!Boolean.TRUE.equals(soup.getIsPublished()) || Boolean.TRUE.equals(soup.getIsDeleted())) {
            throw new BusinessException(400, "该海龟汤不可举报");
        }
        if (isSoupOwner(userId, soup)) {
            throw new BusinessException(400, "无法举报自己的海龟汤");
        }
        if (soupContentReportRepository.existsByReporterIdAndSoupIdAndStatus(
                userId, soup.getSoupId(), SoupContentReportStatus.PENDING)) {
            throw new BusinessException(400, "您已举报过该海龟汤，请等待处理");
        }

        SoupContentReport report = new SoupContentReport();
        report.setSoupId(soup.getSoupId());
        report.setReporterId(userId);
        report.setReasonType(dto.getReasonType());
        report.setReasonDetail(StringUtils.hasText(dto.getReasonDetail())
                ? dto.getReasonDetail().trim() : null);
        report.setStatus(SoupContentReportStatus.PENDING);
        soupContentReportRepository.save(report);

        log.info("海龟汤举报提交 → reportId={}, soupId={}, reporterId={}",
                report.getReportId(), soup.getSoupId(), userId);
        return toItemVo(report, soup, resolveUsername(userId));
    }

    public SoupContentReportListVO listReports(QuerySoupReportListDTO dto) {
        requireAuditPermission();
        int pageNum = dto != null && dto.getPageNum() != null && dto.getPageNum() > 0
                ? dto.getPageNum() : 1;
        int pageSize = dto != null && dto.getPageSize() != null && dto.getPageSize() > 0
                ? Math.min(dto.getPageSize(), 50) : 10;
        PageRequest pageable = PageRequest.of(pageNum - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<SoupContentReport> page;
        if (dto != null && StringUtils.hasText(dto.getStatus())) {
            SoupContentReportStatus status = SoupContentReportStatus.valueOf(dto.getStatus().trim());
            page = soupContentReportRepository.findByStatus(status, pageable);
        } else {
            page = soupContentReportRepository.findByStatusNot(SoupContentReportStatus.CANCELLED, pageable);
        }

        Set<String> soupIds = page.getContent().stream()
                .map(SoupContentReport::getSoupId)
                .collect(Collectors.toSet());
        Map<String, HaiGuiSoup> soupMap = haiGuiSoupRepository.findAllById(soupIds).stream()
                .collect(Collectors.toMap(HaiGuiSoup::getSoupId, s -> s));

        Set<Long> reporterIds = page.getContent().stream()
                .map(SoupContentReport::getReporterId)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = userInfoRepository.findAllById(reporterIds).stream()
                .collect(Collectors.toMap(UserInfo::getUserId, UserInfo::getUsername));

        List<SoupContentReportItemVO> list = page.getContent().stream()
                .map(r -> toItemVo(r, soupMap.get(r.getSoupId()), nameMap.get(r.getReporterId())))
                .toList();

        return SoupContentReportListVO.builder()
                .list(list)
                .total(page.getTotalElements())
                .pages(page.getTotalPages())
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();
    }

    public SoupContentReportItemVO handleReport(HandleSoupContentReportDTO dto) {
        Long handlerId = requireAuditPermission();
        if (dto == null || dto.getReportId() == null) {
            throw new BusinessException(400, "举报信息无效");
        }
        if (!StringUtils.hasText(dto.getAction())) {
            throw new BusinessException(400, "请选择处理方式");
        }

        SoupContentReport report = soupContentReportRepository.findById(dto.getReportId())
                .orElseThrow(() -> new BusinessException(404, "举报记录不存在"));
        if (report.getStatus() != SoupContentReportStatus.PENDING) {
            throw new BusinessException(400, "该举报已处理");
        }

        String action = dto.getAction().trim().toUpperCase();
        String note = StringUtils.hasText(dto.getHandleNote()) ? dto.getHandleNote().trim() : null;
        LocalDateTime now = LocalDateTime.now();

        if (ACTION_DISMISS.equals(action)) {
            report.setStatus(SoupContentReportStatus.DISMISSED);
            report.setHandlerId(handlerId);
            report.setHandleNote(note);
            report.setHandledAt(now);
            soupContentReportRepository.save(report);
            log.info("海龟汤举报驳回 → reportId={}, handlerId={}", report.getReportId(), handlerId);
        } else if (ACTION_TAKE_DOWN.equals(action)) {
            soupTakedownService.takeDownSoup(report.getSoupId(), note);

            List<SoupContentReport> related = soupContentReportRepository.findBySoupIdAndStatus(
                    report.getSoupId(), SoupContentReportStatus.PENDING);
            for (SoupContentReport pending : related) {
                pending.setStatus(SoupContentReportStatus.SOUP_TAKEN_DOWN);
                pending.setHandlerId(handlerId);
                pending.setHandleNote(note);
                pending.setHandledAt(now);
            }
            soupContentReportRepository.saveAll(related);
            log.info("海龟汤举报下架 → reportId={}, soupId={}, handlerId={}",
                    report.getReportId(), report.getSoupId(), handlerId);
        } else {
            throw new BusinessException(400, "无效的处理方式");
        }

        HaiGuiSoup soup = haiGuiSoupRepository.findById(report.getSoupId()).orElse(null);
        return toItemVo(report, soup, resolveUsername(report.getReporterId()));
    }

    public SoupContentReportListVO listMyReports(QuerySoupReportListDTO dto) {
        Long userId = requireLoginUser();
        int pageNum = dto != null && dto.getPageNum() != null && dto.getPageNum() > 0
                ? dto.getPageNum() : 1;
        int pageSize = dto != null && dto.getPageSize() != null && dto.getPageSize() > 0
                ? Math.min(dto.getPageSize(), 50) : 10;
        PageRequest pageable = PageRequest.of(pageNum - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<SoupContentReport> page = soupContentReportRepository
                .findByReporterIdOrderByCreatedAtDesc(userId, pageable);

        Set<String> soupIds = page.getContent().stream()
                .map(SoupContentReport::getSoupId)
                .collect(Collectors.toSet());
        Map<String, HaiGuiSoup> soupMap = haiGuiSoupRepository.findAllById(soupIds).stream()
                .collect(Collectors.toMap(HaiGuiSoup::getSoupId, s -> s));

        List<SoupContentReportItemVO> list = page.getContent().stream()
                .map(r -> toItemVo(r, soupMap.get(r.getSoupId()), resolveUsername(userId)))
                .toList();

        return SoupContentReportListVO.builder()
                .list(list)
                .total(page.getTotalElements())
                .pages(page.getTotalPages())
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();
    }

    public SoupContentReportItemVO withdrawMyReport(Long reportId) {
        Long userId = requireLoginUser();
        SoupContentReport report = soupContentReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(404, "举报记录不存在"));
        if (!userId.equals(report.getReporterId())) {
            throw new BusinessException(403, "无权操作此举报");
        }
        if (report.getStatus() != SoupContentReportStatus.PENDING) {
            throw new BusinessException(400, "仅待处理的举报可撤回");
        }
        report.setStatus(SoupContentReportStatus.CANCELLED);
        report.setHandledAt(LocalDateTime.now());
        soupContentReportRepository.save(report);
        log.info("海龟汤举报撤回 → reportId={}, reporterId={}", reportId, userId);

        HaiGuiSoup soup = haiGuiSoupRepository.findById(report.getSoupId()).orElse(null);
        return toItemVo(report, soup, resolveUsername(userId));
    }

    private boolean isSoupOwner(Long userId, HaiGuiSoup soup) {
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

    private SoupContentReportItemVO toItemVo(SoupContentReport report, HaiGuiSoup soup, String reporterName) {
        return SoupContentReportItemVO.builder()
                .reportId(report.getReportId())
                .soupId(report.getSoupId())
                .soupTitle(soup != null ? soup.getSoupTitle() : "")
                .soupSurface(soup != null ? soup.getSoupSurface() : "")
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

    private static String statusLabel(SoupContentReportStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case PENDING -> "待处理";
            case DISMISSED -> "已驳回";
            case SOUP_TAKEN_DOWN -> "已下架海龟汤";
            case CANCELLED -> "已撤回";
        };
    }
}
