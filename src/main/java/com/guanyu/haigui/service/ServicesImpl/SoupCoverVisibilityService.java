package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.CoverReportStatus;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.repository.SoupCoverReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SoupCoverVisibilityService {

    private final SoupCoverReportRepository soupCoverReportRepository;

    /** 对外展示封面：有待处理举报时隐藏，不影响游玩数据 */
    public String resolvePublicCoverUrl(String soupId, String coverUrl) {
        if (!StringUtils.hasText(coverUrl)) {
            return "";
        }
        if (soupCoverReportRepository.existsBySoupIdAndCoverUrlAndStatus(
                soupId, coverUrl, CoverReportStatus.PENDING)) {
            return "";
        }
        return coverUrl;
    }

    public Set<String> findMaskedCoverKeys(Collection<String> soupIds) {
        if (soupIds == null || soupIds.isEmpty()) {
            return Set.of();
        }
        List<com.guanyu.haigui.pojo.model.SoupCoverReport> pending =
                soupCoverReportRepository.findBySoupIdInAndStatus(soupIds, CoverReportStatus.PENDING);
        Set<String> keys = new HashSet<>();
        for (var report : pending) {
            keys.add(maskKey(report.getSoupId(), report.getCoverUrl()));
        }
        return keys;
    }

    public String maskIfReported(String soupId, String coverUrl, Set<String> maskedKeys) {
        if (!StringUtils.hasText(coverUrl)) {
            return "";
        }
        if (maskedKeys != null && maskedKeys.contains(maskKey(soupId, coverUrl))) {
            return "";
        }
        return coverUrl;
    }

    public static String maskKey(String soupId, String coverUrl) {
        return soupId + "\0" + coverUrl;
    }
}
