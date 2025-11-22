package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.RankingStatistics;
import com.guanyu.haigui.pojo.vo.HotSoupItem;
import com.guanyu.haigui.pojo.vo.SoupRankInfo;
import com.guanyu.haigui.utils.RedisStackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
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

    /**
     * 获取最火爆的前十个海龟汤
     * @return 热门海龟汤排行（包含详细信息）
     */
    public List<HotSoupItem> getTop10HotSoups() {
        try {
            log.info("开始获取热门海龟汤TOP10");

            // 1. 获取热度排行榜前10名
            Map<String, Double> hotnessRanking = redisStackClient.getHotnessRanking(10);

            if (hotnessRanking.isEmpty()) {
                log.warn("热度排行榜为空");
                return new ArrayList<>();
            }

            // 2. 转换为热门海龟汤项目列表
            List<HotSoupItem> hotSoups = new ArrayList<>();
            int rank = 1;

            for (Map.Entry<String, Double> entry : hotnessRanking.entrySet()) {
                String soupId = entry.getKey();
                Double hotnessScore = entry.getValue();

                // 获取海龟汤详细信息
                HaiGuiSoup soup = getSoupById(soupId);
                if (soup != null) {
                    HotSoupItem item = HotSoupItem.builder()
                            .rank(rank)
                            .soupId(soupId)
                            .title(soup.getSoupTitle())
                            .surface(soup.getSoupSurface())
                            .playCount(soup.getPlayCount())
                            .hotnessScore(hotnessScore)
                            .createdAt(soup.getCreatedAt())
                            .build();

                    hotSoups.add(item);
                    rank++;
                }
            }

            log.info("获取热门海龟汤TOP10完成，返回{}个结果", hotSoups.size());
            return hotSoups;

        } catch (Exception e) {
            log.error("获取热门海龟汤TOP10失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取近期热门榜单
     * @param days 近期天数（默认7天）
     * @param topN 返回前N名（默认10名）
     * @return 近期热门海龟汤排行
     */
    public List<HotSoupItem> getRecentHotSoups(int days, int topN) {
        try {
            log.info("开始获取近期热门榜单: days={}, topN={}", days, topN);

            Map<String, Double> recentHotSoups = redisStackClient.getRecentHotSoups(days, topN);

            if (recentHotSoups.isEmpty()) {
                log.warn("近期热门榜单为空");
                return new ArrayList<>();
            }

            List<HotSoupItem> hotSoups = new ArrayList<>();
            int rank = 1;

            for (Map.Entry<String, Double> entry : recentHotSoups.entrySet()) {
                String soupId = entry.getKey();
                Double recentScore = entry.getValue();

                HaiGuiSoup soup = getSoupById(soupId);
                if (soup != null) {
                    HotSoupItem item = HotSoupItem.builder()
                            .rank(rank)
                            .soupId(soupId)
                            .title(soup.getSoupTitle())
                            .surface(soup.getSoupSurface())
                            .playCount(soup.getPlayCount())
                            .hotnessScore(recentScore)
                            .createdAt(soup.getCreatedAt())
                            .build();

                    hotSoups.add(item);
                    rank++;
                }
            }

            log.info("获取近期热门榜单完成，返回{}个结果", hotSoups.size());
            return hotSoups;

        } catch (Exception e) {
            log.error("获取近期热门榜单失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 记录用户玩海龟汤的行为（用于热度计算）
     * @param soupId 海龟汤ID
     * @param userId 用户ID
     * @param action 行为类型（play, like, share, comment）
     */
    public void recordUserAction(String soupId, Long userId, String action) {
        try {
            double hotnessIncrement;

            switch (action.toLowerCase()) {
                case "play":
                    hotnessIncrement = 10.0;
                    redisStackClient.incrementSoupPlayCount(soupId);
                    break;
                case "like":
                    hotnessIncrement = 20.0;
                    break;
                case "share":
                    hotnessIncrement = 50.0;
                    break;
                case "comment":
                    hotnessIncrement = 15.0;
                    break;
                default:
                    log.warn("未知的用户行为: action={}", action);
                    return;
            }

            // 增加热度分数
            redisStackClient.incrementSoupHotness(soupId, hotnessIncrement);

            // 记录用户行为统计
            String userActionKey = String.format("hai_gui:user:%s:actions", userId);
            redisStackClient.getCommands().hincrby(userActionKey, soupId + ":" + action, 1);
            redisStackClient.getCommands().expire(userActionKey, 30 * 24 * 60 * 60);

            log.info("记录用户行为: userId={}, soupId={}, action={}, increment={}",
                    userId, soupId, action, hotnessIncrement);

        } catch (Exception e) {
            log.error("记录用户行为失败: userId={}, soupId={}, action={}", userId, soupId, action, e);
        }
    }

    /**
     * 获取海龟汤当前热度排名
     * @param soupId 海龟汤ID
     * @return 排名信息
     */
    public SoupRankInfo getSoupRankInfo(String soupId) {
        try {
            Double hotnessScore = redisStackClient.getSoupHotness(soupId);
            if (hotnessScore == null || hotnessScore == 0.0) {
                return SoupRankInfo.builder()
                        .soupId(soupId)
                        .currentRank(-1)
                        .hotnessScore(0.0)
                        .isInTop10(false)
                        .build();
            }

            // 获取完整排行榜来计算排名
            Map<String, Double> fullRanking = redisStackClient.getHotnessRanking(100);
            int rank = 1;
            boolean found = false;

            for (Map.Entry<String, Double> entry : fullRanking.entrySet()) {
                if (soupId.equals(entry.getKey())) {
                    found = true;
                    break;
                }
                rank++;
            }

            return SoupRankInfo.builder()
                    .soupId(soupId)
                    .currentRank(found ? rank : -1)
                    .hotnessScore(hotnessScore)
                    .isInTop10(found && rank <= 10)
                    .build();

        } catch (Exception e) {
            log.error("获取海龟汤排名信息失败: soupId={}", soupId, e);
            return SoupRankInfo.builder()
                    .soupId(soupId)
                    .currentRank(-1)
                    .hotnessScore(0.0)
                    .isInTop10(false)
                    .build();
        }
    }

    /**
     * 更新海龟汤热度（基于数据库统计数据）
     * @param soupId 海龟汤ID
     * @param playCount 播放次数
     * @param likeCount 点赞数
     * @param shareCount 分享数
     * @param commentCount 评论数
     */
    public void updateSoupHotness(String soupId, int playCount, int likeCount,
                                 int shareCount, int commentCount) {
        try {
            redisStackClient.updateSoupHotness(soupId, playCount, likeCount, shareCount, commentCount);
        } catch (Exception e) {
            log.error("更新海龟汤热度失败: soupId={}", soupId, e);
        }
    }

    /**
     * 清理过期的热度数据（定时任务使用）
     */
    public void cleanupExpiredData() {
        try {
            redisStackClient.cleanupExpiredHotnessData();
        } catch (Exception e) {
            log.error("清理过期热度数据失败", e);
        }
    }

    /**
     * 获取榜单统计信息
     * @return 榜单统计数据
     */
    public RankingStatistics getRankingStatistics() {
        try {
            Map<String, Double> top10Ranking = redisStackClient.getHotnessRanking(10);
            Map<String, Double> top100Ranking = redisStackClient.getHotnessRanking(100);

            // 计算总热度分数
            double totalHotness = top100Ranking.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();

            // 获取TOP10的热度占比
            double top10Hotness = top10Ranking.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();

            return RankingStatistics.builder()
                    .totalRankedSoups(top100Ranking.size())
                    .top10TotalHotness(top10Hotness)
                    .totalHotness(totalHotness)
                    .top10Percentage(totalHotness > 0 ? (top10Hotness / totalHotness * 100) : 0.0)
                    .build();

        } catch (Exception e) {
            log.error("获取榜单统计信息失败", e);
            return RankingStatistics.builder().build();
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
                    soup.setCreatedAt(new Date(Long.parseLong(createdAtStr.trim())));
                } catch (NumberFormatException e) {
                    log.warn("解析创建时间失败: soupId={}, createdAtStr={}", soupId, createdAtStr);
                    soup.setCreatedAt(new Date());
                }
            }

            return soup;

        } catch (Exception e) {
            log.error("获取海龟汤信息失败: soupId={}", soupId, e);
            return null;
        }
    }






}