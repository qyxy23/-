package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.pojo.dto.SoupProjectionDTO;
import com.guanyu.haigui.pojo.model.SoupListPageResponse;
import com.guanyu.haigui.pojo.vo.SoupListItem;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 海龟汤榜单服务
 * 提供热点数据统计和排行榜功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HaiGuiRankingService {

    private final HaiGuiSoupRepository haiGuiSoupRepository;



    /**
     * 获取海龟汤分页列表
     *
     * @param page            页码（从1开始）
     * @param pageSize        每页大小，默认10
     * @param filterParams    筛选参数Map
     * @return 分页后的海龟汤列表
     */
    public SoupListPageResponse getSoupListWithPagination(int page, int pageSize,
                                                          Map<String, Object> filterParams) {
        try {
            // 提取筛选条件
            SoupTag tags = null;
            Object tagsObj = filterParams.get("tags");
            if (tagsObj != null) {
                if (tagsObj instanceof SoupTag) {
                    tags = (SoupTag) tagsObj;
                } else if (tagsObj instanceof String) {
                    // 使用新的fromString方法
                    tags = SoupTag.fromString((String) tagsObj);
                }
            }

            // 处理难度筛选参数 - 转换为枚举类型
            DifficultyLevel difficultyLevel = null;
            Object difficultyObj = filterParams.get("difficultyLevel");
            if (difficultyObj != null) {
                if (difficultyObj instanceof DifficultyLevel) {
                    difficultyLevel = (DifficultyLevel) difficultyObj;
                } else if (difficultyObj instanceof String diffStr) {
                    if (!diffStr.isBlank()) {
                        try {
                            difficultyLevel = DifficultyLevel.valueOf(diffStr.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            log.warn("无效的难度等级: {}", diffStr);
                        }
                    }
                }
            }
            Integer playerCount = (Integer) filterParams.get("playerCount");
            Integer minDuration = (Integer) filterParams.get("minDuration");
            Integer maxDuration = (Integer) filterParams.get("maxDuration");

            // 参数校验和分页设置
            if (page < 1) page = 1;
            if (pageSize < 1) pageSize = 10;
            if (pageSize > 100) pageSize = 100;
            Pageable pageable = PageRequest.of(page - 1, pageSize);

            // 处理筛选参数
            Integer playerCountParam = (playerCount != null && playerCount > 0) ? playerCount : null;

            // 查询分页数据
            Page<SoupProjectionDTO> soupPage = haiGuiSoupRepository.findSoupsWithPagination(
                    pageable, tags, difficultyLevel, playerCountParam, minDuration, maxDuration);
            // 转换为VO列表
            List<SoupListItem> soupList = soupPage.getContent().stream()
                    .map(this::convertToSoupListItem)
                    .toList();

            // 构建分页响应
            return SoupListPageResponse.builder()
                    .soupList(soupList)
                    .currentPage(page)
                    .pageSize(pageSize)
                    .totalRecords((int) soupPage.getTotalElements())
                    .totalPages(soupPage.getTotalPages())
                    .hasNext(soupPage.hasNext())
                    .hasPrevious(soupPage.hasPrevious())
                    .build();
        } catch (Exception e) {
            log.error("获取海龟汤分页列表失败", e);
            return SoupListPageResponse.builder()
                    .soupList(new ArrayList<>())
                    .currentPage(page)
                    .pageSize(pageSize)
                    .totalRecords(0)
                    .totalPages(0)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build();
        }
    }

    /**
     * 获取已发布海龟汤公开详情（不含汤底、线索、任务、手册）
     */
    public SoupListItem getSoupBrief(String soupId) {
        if (soupId == null || soupId.isBlank()) {
            throw new BusinessException(400, "海龟汤 ID 不能为空");
        }
        return haiGuiSoupRepository.findPublishedSoupBriefById(soupId.trim())
                .map(this::convertToSoupListItem)
                .orElseThrow(() -> new BusinessException(404, "海龟汤不存在或未发布"));
    }

    private SoupListItem convertToSoupListItem(SoupProjectionDTO projection) {
        try {
            return SoupListItem.builder()
                    .soupId(projection.getSoupId())
                    .soupTitle(projection.getSoupTitle())
                    .soupSurface(projection.getSoupSurface())
                    .playCount(projection.getPlayCount())
                    .uploaderId(projection.getUploaderId())
                    .uploaderAvatar(projection.getUploaderAvatar())
                    .uploadTime(projection.getUploadTime())
                    .difficultyLevel(projection.getDifficultyLevel())
                    .playerCount(projection.getPlayerCount())
                    .tag(projection.getTag() != null ? projection.getTag().getDescription() : "未知标签")
                    .soupAvatar(projection.getSoupAvatar())
                    .estimatedDuration(projection.getEstimatedDuration())
                    .defaultMaxQuestions(projection.getDefaultMaxQuestions())
                    .build();
        } catch (Exception e) {
            log.error("转换海龟汤投影到VO失败: soupId={}", projection.getSoupId(), e);
            return SoupListItem.builder()
                    .soupId(projection.getSoupId())
                    .soupTitle(projection.getSoupTitle())
                    .soupSurface(projection.getSoupSurface())
                    .playCount(projection.getPlayCount())
                    .uploaderId(projection.getUploaderId())
                    .uploaderAvatar(projection.getUploaderAvatar())
                    .uploadTime(projection.getUploadTime())
                    .difficultyLevel(projection.getDifficultyLevel())
                    .playerCount(projection.getPlayerCount())
                    .tag(projection.getTag() != null ? projection.getTag().getDescription() : "")
                    .estimatedDuration(projection.getEstimatedDuration() != null ?
                            projection.getEstimatedDuration() : 30)
                    .defaultMaxQuestions(projection.getDefaultMaxQuestions() != null ?
                            projection.getDefaultMaxQuestions() : 30)
                    .build();
        }
    }
}