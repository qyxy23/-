package com.guanyu.haigui.controller;

import com.guanyu.haigui.Enum.SoupTag;
import com.guanyu.haigui.pojo.model.SoupListPageResponse;
import com.guanyu.haigui.pojo.vo.SoupListItem;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.ServicesImpl.HaiGuiRankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public Result<SoupListPageResponse> getSoupListWithPagination(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String difficultyLevel,
            @RequestParam(required = false) Integer playerCount,
            @RequestParam(required = false) Integer duration) {

        // 参数校验
        if (page < 1) return Result.error("页码必须大于0");
        if (pageSize < 1 || pageSize > 100) return Result.error("每页大小必须在1-100之间");

        // 构建筛选条件
        Map<String, Object> filterParams = new HashMap<>();

        // 处理标签筛选 - 使用新的fromString方法
        if (tag != null && !tag.isEmpty()) {
            SoupTag soupTag = SoupTag.fromString(tag);
            filterParams.put("tags", soupTag);
        }

        // 处理难度筛选（忽略空字符串，避免前端未传参时产生无效枚举警告）
        if (difficultyLevel != null && !difficultyLevel.isBlank()) {
            filterParams.put("difficultyLevel", difficultyLevel);
        }

        // 处理人数筛选
        if (playerCount != null && playerCount > 0) {
            filterParams.put("playerCount", playerCount);
        }

        // 处理时长筛选
        if (duration != null) {
            switch (duration) {
                case 1:
                    filterParams.put("minDuration", null);
                    filterParams.put("maxDuration", 60);
                    break;
                case 2:
                    filterParams.put("minDuration", 60);
                    filterParams.put("maxDuration", 120);
                    break;
                case 3:
                    filterParams.put("minDuration", 120);
                    filterParams.put("maxDuration", 180);
                    break;
                case 4:
                    filterParams.put("minDuration", 180);
                    filterParams.put("maxDuration", 300);
                    break;
                case 5:
                    filterParams.put("minDuration", 300);
                    filterParams.put("maxDuration", null);
                    break;
                default:
                    filterParams.put("minDuration", null);
                    filterParams.put("maxDuration", null);
            }
        }

        try {
            SoupListPageResponse response = haiGuiRankingService.getSoupListWithPagination(
                    page, pageSize, filterParams);
            return Result.success("获取海龟汤列表成功", response);
        } catch (Exception e) {
            log.error("获取海龟汤列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取海龟汤公开详情", description = "返回标题、汤面、封面及游玩参数，不含汤底/线索/任务/手册")
    @GetMapping("/soup/{soupId}")
    public Result<SoupListItem> getSoupBrief(@PathVariable String soupId) {
        try {
            return Result.success(haiGuiRankingService.getSoupBrief(soupId));
        } catch (Exception e) {
            log.error("获取海龟汤详情失败 soupId={}", soupId, e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }
}