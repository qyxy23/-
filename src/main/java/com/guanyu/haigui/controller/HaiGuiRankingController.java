package com.guanyu.haigui.controller;

import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import com.guanyu.haigui.pojo.model.SoupListPageResponse;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.ServicesImpl.HaiGuiRankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 海龟汤榜单控制器
 * 提供热点数据和排行榜功能
 */
@RestController
@RequestMapping("/api/haigui/ranking")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "海龟汤榜单", description = "海龟汤热点数据和排行榜相关接口")
public class HaiGuiRankingController {

    private final HaiGuiRankingService haiGuiRankingService;

    /**
     * 获取海龟汤分页列表
     * @param page 页码（从1开始）
     * @param pageSize 每页大小，默认10
     * @param tag 标签筛选：惊悚、欢乐、情感、脑洞、奇幻、日常、其他
     * @param difficultyLevel 难度筛选：入门、中等、困难
     * @param playerCount 人数筛选：指定游玩人数，0表示不限制
     * @param duration 时长筛选：1(1小时以下)、2(1-2小时)、3(2-3小时)、4(3-5小时)、5(5小时以上)
     * @return 分页后的海龟汤列表
     */
    @GetMapping("/soup-list")
    @Operation(summary = "获取海龟汤列表", description = "分页查询海龟汤列表，支持按标签、难度、人数、时长筛选")
    public Result<SoupListPageResponse> getSoupListWithPagination(
            @Parameter(description = "页码，从1开始，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10，最大100") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "标签筛选：惊悚、欢乐、情感、脑洞、奇幻、日常、其他") @RequestParam(required = false) String tag,
            @Parameter(description = "难度筛选：入门、中等、困难") @RequestParam(required = false) String difficultyLevel,
            @Parameter(description = "人数筛选：指定游玩人数，0表示不限制") @RequestParam(required = false) Integer playerCount,
            @Parameter(description = "时长筛选：1(1小时以下)、2(1-2小时)、3(2-3小时)、4(3-5小时)、5(5小时以上)") @RequestParam(required = false) Integer duration) {

        // 参数校验
        if (page < 1) {
            return Result.error("页码必须大于0");
        }
        if (pageSize < 1 || pageSize > 100) {
            return Result.error("每页大小必须在1-100之间");
        }

        // 构建筛选条件
        Map<String, Object> filterParams = new HashMap<>();

        // 处理标签筛选
        if (tag != null && !tag.isEmpty()) {
            SoupTag filteredTags = SoupTag.fromString(tag);
            if (filteredTags!= null) {
                log.info("用户选择了标签: {}", filteredTags);
                filterParams.put("tags", filteredTags);
            } else {
                log.info("用户没有选择有效标签");
            }
        }

        // 处理难度筛选
        if (difficultyLevel != null) {
            try {
                DifficultyLevel filterLevel = DifficultyLevel.valueOf(difficultyLevel.toUpperCase());
                filterParams.put("difficultyLevel", filterLevel);
            } catch (IllegalArgumentException e) {
                log.warn("无效的难度等级: {}", difficultyLevel);
                filterParams.put("difficultyLevel", null);
            }
        }

        // 处理人数筛选
        if (playerCount != null && playerCount != 0) {
            if (playerCount > 0 && playerCount <= 10) {
                filterParams.put("playerCount", playerCount);
            } else if (playerCount > 10) {
                log.warn("人数限制超过最大值，设置为10");
                filterParams.put("playerCount", 10);
            } else {
                log.warn("人数限制必须为正整数");
                filterParams.put("playerCount", 0);
            }
        } else {
            filterParams.put("playerCount", 0);
        }

        // 处理时长筛选
        if (duration != null) {
            switch (duration) {
                case 1: // 1小时以下
                    filterParams.put("minDuration", null);
                    filterParams.put("maxDuration", 60);
                    break;
                case 2: // 1-2小时
                    filterParams.put("minDuration", 60);
                    filterParams.put("maxDuration", 120);
                    break;
                case 3: // 2-3小时
                    filterParams.put("minDuration", 120);
                    filterParams.put("maxDuration", 180);
                    break;
                case 4: // 3-5小时
                    filterParams.put("minDuration", 180);
                    filterParams.put("maxDuration", 300);
                    break;
                case 5: // 5小时以上
                    filterParams.put("minDuration", 300);
                    filterParams.put("maxDuration", null);
                    break;
                default:
                    log.warn("无效的时长范围: {}", duration);
                    filterParams.put("minDuration", null);
                    filterParams.put("maxDuration", null);
                    break;
            }
        } else {
            filterParams.put("minDuration", null);
            filterParams.put("maxDuration", null);
        }

        try {
            log.info("开始获取海龟汤分页列表: page={}, pageSize={}", page, pageSize);
            log.info("筛选条件: tags={}, difficulty={}, playerCount={}, duration={}",
                    tag, difficultyLevel, playerCount, duration);

            SoupListPageResponse response = haiGuiRankingService.getSoupListWithPagination(page, pageSize, filterParams);
            return Result.success("获取海龟汤列表成功", response);
        } catch (Exception e) {
            log.error("获取海龟汤列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }
}