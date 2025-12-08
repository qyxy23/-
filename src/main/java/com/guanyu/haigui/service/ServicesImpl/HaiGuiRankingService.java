package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import com.guanyu.haigui.pojo.dto.SoupProjectionDTO;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.SoupListPageResponse;
import com.guanyu.haigui.pojo.vo.SoupListItem;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.utils.RedisStackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private final RedisStackClient redisStackClient;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final ObjectMapper objectMapper;



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
            // 从filterParams中提取筛选条件
            String tags = null;
            Object tagsObj = filterParams.get("tags");
            if (tagsObj != null) {
                if (tagsObj instanceof SoupTag) {
                    // 如果是SoupTag对象，直接取描述
                    tags = ((SoupTag) tagsObj).getDescription();
                } else if (tagsObj instanceof String) {
                    // 如果是字符串（比如前端传的标签枚举名），尝试转换为SoupTag
                    try {
                        SoupTag soupTag = SoupTag.valueOf((String) tagsObj);
                        tags = soupTag.getDescription();
                    } catch (IllegalArgumentException e) {
                        // 处理无效的标签枚举值（比如拼写错误）
                        log.warn("无效的标签枚举值: {}", tagsObj, e);
                    }
                } else {
                    // 其他未知类型，记录警告并设为null
                    log.warn("未知的标签参数类型: {}", tagsObj.getClass());
                }
            }
            String difficultyLevel = (String) filterParams.get("difficultyLevel");
            Integer playerCount = (Integer) filterParams.get("playerCount");
            Integer minDuration = (Integer) filterParams.get("minDuration");
            Integer maxDuration = (Integer) filterParams.get("maxDuration");

            log.info("开始获取海龟汤分页列表: page={}, pageSize={}", page, pageSize);
            log.info("筛选条件: tags={}, difficulty={}, playerCount={}, minDuration={}, maxDuration={}",
                    tags, difficultyLevel, playerCount, minDuration, maxDuration);

            // 参数校验
            if (page < 1) {
                page = 1;
            }
            if (pageSize < 1) {
                pageSize = 10;
            }
            if (pageSize > 100) {
                pageSize = 100;
            }

            // 创建分页参数
            Pageable pageable = PageRequest.of(page - 1, pageSize);

            // 处理标签筛选参数 - 支持多个标签筛选
            String tagParam;
            if (tags != null && !tags.isEmpty()) {
                // 对于单个标签筛选，使用第一个标签
                tagParam = tags;
                log.info("标签筛选参数: 原始标签列表={}, 查询标签={}", tags, tagParam);
            } else {
                // 处理tags为null或空的情况
                log.info("用户没有选择标签，或标签为空，将使用null进行筛选");
                tagParam = null;
            }

            // 处理难度筛选参数
            String difficultyParam = null;
            if (difficultyLevel != null) {
                difficultyParam = difficultyLevel;
            }

            // 处理人数筛选参数
            Integer playerCountParam = null;
            if (playerCount != null && playerCount > 0) {
                playerCountParam = playerCount;
            }

            // 处理时长筛选参数

            // 查询分页数据
            log.info("执行数据库查询 - tagParam={}, difficultyParam={}, playerCountParam={}, minDuration={}, maxDuration={}",
                     tagParam, difficultyParam, playerCountParam, minDuration, maxDuration);
            Page<SoupProjectionDTO> soupPage =
                    haiGuiSoupRepository.findSoupsWithPagination(pageable, tagParam, difficultyParam, playerCountParam, minDuration, maxDuration);

            // 转换为VO列表
            List<SoupListItem> soupList = soupPage.getContent().stream()
                    .map(this::convertToSoupListItem)
                    .toList();

            // 构建分页响应
            SoupListPageResponse response = SoupListPageResponse.builder()
                    .soupList(soupList)
                    .currentPage(page)
                    .pageSize(pageSize)
                    .totalRecords((int) soupPage.getTotalElements())
                    .totalPages(soupPage.getTotalPages())
                    .hasNext(soupPage.hasNext())
                    .hasPrevious(soupPage.hasPrevious())
                    .build();

            log.info("获取海龟汤分页列表完成，返回{}条记录，总记录数: {}",
                    soupList.size(), soupPage.getTotalElements());

            return response;

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
     * 将投影DTO转换为VO
     */
    private SoupListItem convertToSoupListItem(SoupProjectionDTO projection) {
        try {
            // 构建基础信息
            SoupListItem.SoupListItemBuilder builder = SoupListItem.builder()
                    .soupId(projection.getSoupId())
                    .soupTitle(projection.getSoupTitle())
                    .soupSurface(projection.getSoupSurface())
                    .playCount(projection.getPlayCount())
                    .uploaderId(projection.getUploaderId())
                    .uploaderAvatar(projection.getUploaderAvatar())
                    .uploadTime(projection.getUploadTime())
                    .difficultyLevel(projection.getDifficultyLevel())
                    .playerCount(projection.getPlayerCount())
                    .tag(projection.getTag())
                    .soupAvatar(projection.getSoupAvatar())
                    .estimatedDuration(projection.getEstimatedDuration());
            return builder.build();
        } catch (Exception e) {
            log.error("转换海龟汤投影到VO失败: soupId={}", projection.getSoupId(), e);
            Integer estimatedDuration = projection.getEstimatedDuration();
            return SoupListItem.builder()
                    .soupId(projection.getSoupId())
                    .soupTitle(projection.getSoupTitle())
                    .soupSurface(projection.getSoupSurface())
                    .playCount(projection.getPlayCount())
                    .uploaderId(projection.getUploaderId())
                    .uploaderAvatar(projection.getUploaderAvatar())
                    .uploadTime(projection.getUploadTime())
                    .difficultyLevel(projection.getDifficultyLevel())
                    .estimatedDuration(estimatedDuration != null ? estimatedDuration : 30)
                    .playerCount(projection.getPlayerCount())
                    .tag(projection.getTag()) // 使用从数据库获取的标签
                    .build();
        }
    }

    /**
     * 转换难度等级
     */
    private DifficultyLevel convertToDifficultyLevel(String difficultyLevel) {
        try {
            return DifficultyLevel.valueOf(difficultyLevel);
        } catch (IllegalArgumentException e) {
            log.warn("无效的难度等级: {}, 使用默认值", difficultyLevel);
            return DifficultyLevel.BEGINNER;
        }
    }

    /**
     * 解析标签JSON字符串
     */
    private java.util.List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.trim().isEmpty() || "null".equals(tagsJson)) {
            return java.util.Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<java.util.List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("解析标签JSON失败: tagsJson={}", tagsJson, e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 根据soupId获取海龟汤详细信息
     * 使用RedisStackClient提供的安全方法，避免类型转换问题
     */
    private HaiGuiSoup getSoupById(String soupId) {
        try {
            // 先检查海龟汤是否存在
            if (!redisStackClient.soupExists(soupId)) {
                log.debug("海龟汤不存在: soupId={}", soupId);
                return null;
            }

            // 使用安全的获取方法
            Map<String, String> soupData = redisStackClient.getSoupInfo(soupId);

            if (soupData.isEmpty()) {
                log.debug("海龟汤数据为空: soupId={}", soupId);
                return null;
            }

            HaiGuiSoup soup = new HaiGuiSoup();
            soup.setSoupId(soupId);
            soup.setSoupTitle(soupData.get("soupTitle"));
            soup.setSoupSurface(soupData.get("soupSurface"));
            soup.setSoupBottom(soupData.get("soupBottom"));
            soup.setHostManual(soupData.get("hostManual"));
            soup.setCreatorId(Long.parseLong(soupData.get("creatorId")));
            soup.setUploaderId(Long.parseLong(soupData.get("uploaderId")));
            soup.setUploadTime(LocalDateTime.parse(soupData.get("uploadTime")));

            // 解析播放次数
            String playCountStr = soupData.get("playCount");
            if (playCountStr != null && !playCountStr.trim().isEmpty()) {
                try {
                    soup.setPlayCount(Integer.parseInt(playCountStr.trim()));
                } catch (NumberFormatException e) {
                    log.warn("解析播放次数失败: soupId={}, playCountStr={}", soupId, playCountStr);
                    soup.setPlayCount(0);
                }
            }

            // 解析创建时间
            String createdAtStr = soupData.get("createdAt");
            if (createdAtStr != null && !createdAtStr.trim().isEmpty()) {
                try {
                    long timestamp = Long.parseLong(createdAtStr.trim());
                    soup.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
                } catch (NumberFormatException e) {
                    log.warn("解析创建时间失败: soupId={}, createdAtStr={}", soupId, createdAtStr);
                    soup.setCreatedAt(LocalDateTime.now());
                }
            }
            return soup;
        } catch (Exception e) {
            log.error("获取海龟汤信息失败: soupId={}", soupId, e);
            return null;
        }
    }
}