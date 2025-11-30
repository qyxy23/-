package com.guanyu.haigui.controller;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.model.MultipleRankingsResponse;
import com.guanyu.haigui.pojo.model.RankingStatistics;
import com.guanyu.haigui.pojo.model.SoupListPageResponse;
import com.guanyu.haigui.pojo.vo.HotSoupItem;
import com.guanyu.haigui.pojo.vo.SoupRankInfo;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.ServicesImpl.HaiGuiRankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * 获取海龟汤列表（分页查询）
     */
    @GetMapping("/soup-list")
    @Operation(summary = "获取海龟汤列表", description = "分页查询海龟汤列表，返回ID、标题、汤面、汤底、游玩次数、上传者信息等")
    public Result<SoupListPageResponse> getSoupListWithPagination(
            @Parameter(description = "页码，从1开始，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10，最大100") @RequestParam(defaultValue = "10") int pageSize) {
        try {
            if (page < 1) {
                return Result.error("页码必须大于0");
            }
            if (pageSize < 1 || pageSize > 100) {
                return Result.error("每页大小必须在1-100之间");
            }

            SoupListPageResponse response = haiGuiRankingService.getSoupListWithPagination(page, pageSize);
            return Result.success("获取海龟汤列表成功", response);

        } catch (Exception e) {
            log.error("获取海龟汤列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取最火爆的前十个海龟汤
     */
    @GetMapping("/top10")
    @Operation(summary = "获取热门TOP10", description = "获取当前最火爆的前十个海龟汤排行榜")
    public Result<List<HotSoupItem>> getTop10HotSoups() {
        try {
            List<HotSoupItem> top10Soups = haiGuiRankingService.getTop10HotSoups();
            return Result.success("获取热门TOP10成功", top10Soups);

        } catch (Exception e) {
            log.error("获取热门TOP10失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取近期热门榜单
     */
    @GetMapping("/recent-hot")
    @Operation(summary = "获取近期热门榜单", description = "基于最近N天数据获取热门海龟汤排行榜")
    public Result<List<HotSoupItem>> getRecentHotSoups(
            @Parameter(description = "最近天数，默认7天") @RequestParam(defaultValue = "7") int days,
            @Parameter(description = "返回前N名，默认10名") @RequestParam(defaultValue = "10") int topN) {
        try {
            if (days <= 0 || days > 30) {
                return Result.error("天数范围应在1-30之间");
            }
            if (topN <= 0 || topN > 50) {
                return Result.error("返回数量范围应在1-50之间");
            }

            List<HotSoupItem> recentHotSoups =
                    haiGuiRankingService.getRecentHotSoups(days, topN);

            return Result.success(String.format("获取最近%d天热门榜单成功", days), recentHotSoups);

        } catch (Exception e) {
            log.error("获取近期热门榜单失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 记录用户行为
     */
    @PostMapping("/record-action")
    @Operation(summary = "记录用户行为", description = "记录用户玩海龟汤的行为，用于热度计算")
    public Result<String> recordUserAction(
            @Parameter(description = "海龟汤ID") @RequestParam String soupId,
            @Parameter(description = "行为类型：play(玩)、like(点赞)、share(分享)、comment(评论)") @RequestParam String action) {
        try {
            if (soupId == null || soupId.trim().isEmpty()) {
                return Result.error("海龟汤ID不能为空");
            }

            if (!isValidAction(action)) {
                return Result.error("无效的行为类型，支持：play、like、share、comment");
            }

            Long userId = BaseContext.getCurrentId();
            haiGuiRankingService.recordUserAction(soupId, userId, action);

            return Result.success("用户行为记录成功");

        } catch (Exception e) {
            log.error("记录用户行为失败", e);
            return Result.error("记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取海龟汤排名信息
     */
    @GetMapping("/soup-rank/{soupId}")
    @Operation(summary = "获取海龟汤排名", description = "获取指定海龟汤的当前热度排名信息")
    public Result<SoupRankInfo> getSoupRankInfo(@PathVariable String soupId) {
        try {
            if (soupId == null || soupId.trim().isEmpty()) {
                return Result.error("海龟汤ID不能为空");
            }

            SoupRankInfo rankInfo = haiGuiRankingService.getSoupRankInfo(soupId);
            return Result.success("获取排名信息成功", rankInfo);

        } catch (Exception e) {
            log.error("获取海龟汤排名信息失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 更新海龟汤热度
     */
    @PostMapping("/update-hotness")
    @Operation(summary = "更新海龟汤热度", description = "基于统计数据更新海龟汤的热度分数")
    public Result<String> updateSoupHotness(
            @Parameter(description = "海龟汤ID") @RequestParam String soupId,
            @Parameter(description = "播放次数") @RequestParam(defaultValue = "0") int playCount,
            @Parameter(description = "点赞数") @RequestParam(defaultValue = "0") int likeCount,
            @Parameter(description = "分享数") @RequestParam(defaultValue = "0") int shareCount,
            @Parameter(description = "评论数") @RequestParam(defaultValue = "0") int commentCount) {
        try {
            if (soupId == null || soupId.trim().isEmpty()) {
                return Result.error("海龟汤ID不能为空");
            }

            haiGuiRankingService.updateSoupHotness(soupId, playCount, likeCount, shareCount, commentCount);
            return Result.success("热度更新成功");

        } catch (Exception e) {
            log.error("更新海龟汤热度失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 获取榜单统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取榜单统计", description = "获取排行榜的统计信息")
    public Result<RankingStatistics> getRankingStatistics() {
        try {
            RankingStatistics statistics = haiGuiRankingService.getRankingStatistics();
            return Result.success("获取统计信息成功", statistics);

        } catch (Exception e) {
            log.error("获取榜单统计信息失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期数据
     */
    @PostMapping("/cleanup")
    @Operation(summary = "清理过期数据", description = "清理过期的热度统计数据（定时任务使用）")
    public Result<String> cleanupExpiredData() {
        try {
            haiGuiRankingService.cleanupExpiredData();
            return Result.success("过期数据清理完成");

        } catch (Exception e) {
            log.error("清理过期数据失败", e);
            return Result.error("清理失败: " + e.getMessage());
        }
    }

    /**
     * 获取多种榜单数据
     */
    @GetMapping("/all-rankings")
    @Operation(summary = "获取多种榜单", description = "一次性获取多种类型的榜单数据")
    public Result<MultipleRankingsResponse> getAllRankings(
            @Parameter(description = "近期天数，默认7天") @RequestParam(defaultValue = "7") int days,
            @Parameter(description = "返回前N名，默认10名") @RequestParam(defaultValue = "10") int topN) {
        try {
            // 获取热门TOP10
            List<HotSoupItem> top10 = haiGuiRankingService.getTop10HotSoups();

            // 获取近期热门
            List<HotSoupItem> recentHot = haiGuiRankingService.getRecentHotSoups(days, topN);

            // 获取统计信息
            RankingStatistics statistics = haiGuiRankingService.getRankingStatistics();

            MultipleRankingsResponse response = MultipleRankingsResponse.builder()
                    .top10(top10)
                    .recentHot(recentHot)
                    .statistics(statistics)
                    .build();

            return Result.success("获取榜单数据成功", response);

        } catch (Exception e) {
            log.error("获取多种榜单数据失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 验证行为类型是否有效
     */
    private boolean isValidAction(String action) {
        return "play".equalsIgnoreCase(action) ||
               "like".equalsIgnoreCase(action) ||
               "share".equalsIgnoreCase(action) ||
               "comment".equalsIgnoreCase(action);
    }


}