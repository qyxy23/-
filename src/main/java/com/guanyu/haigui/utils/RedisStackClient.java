package com.guanyu.haigui.utils;

import com.guanyu.haigui.vector.ClueFragmentVectorIndex;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisStackClient {

    private final ClueFragmentVectorIndex clueFragmentVectorIndex;

    @Value("${qingyou.redis.host}")
    private String host;

    @Value("${qingyou.redis.port}")
    private int port;

    @Value("${qingyou.redis.password}")
    private String password;

    private StatefulRedisConnection<String, String> connection;
    /**
     * -- GETTER --
     *  获取Redis命令接口（提供给其他Service使用）
     *
     */
    @Getter
    private RedisCommands<String, String> commands;

    /**
     * 初始化 Redis 连接（Lettuce 异步连接，线程安全）
     */
    @PostConstruct
    public void init() {
        RedisURI redisUri = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withPassword(password != null ? password.toCharArray() : null)
                .build();

        RedisClient client = RedisClient.create(redisUri);
        this.connection = client.connect();
        this.commands = connection.sync(); // 获取同步命令接口
    }

    @PreDestroy
    public void destroy() {
        if (connection != null && connection.isOpen()) {
            connection.close(); // 关闭连接
        }
    }


    /**
     * 确保 RediSearch 向量索引存在
     */
    public void ensureVectorIndex() {
        clueFragmentVectorIndex.ensureIndex();
    }

    /**
     * 删除海龟汤的所有相关数据（包括向量）
     * @param soupId 海龟汤ID
     */
    public void deleteSoup(String soupId) {
        try {
            String soupFragmentsKey = String.format("hai_gui:soup:%s:fragment", soupId);
            Set<String> fragmentIds = commands.smembers(soupFragmentsKey);
            clueFragmentVectorIndex.deleteSoupFragmentVectors(soupId, fragmentIds);

            // 删除基本信息
            commands.del("hai_gui:soup:" + soupId);
            commands.del(soupFragmentsKey);

            // 删除其他类型向量数据
            commands.del("hai_gui:vec:surface:" + soupId);
            commands.del("hai_gui:vec:bottom:" + soupId);
            commands.del("hai_gui:vec:manual:" + soupId);

            // 从集合中移除
            commands.srem("hai_gui:soups:all", soupId);

            log.info("成功删除海龟汤数据: soupId={}", soupId);

        } catch (Exception e) {
            log.error("删除海龟汤数据失败: soupId={}", soupId, e);
        }
    }

    /**
     * 存储线索片段向量（Redis Stack RediSearch 索引）
     */
    public boolean storeClueFragmentVector(String soupId, String fragmentId, List<Double> vector) {
        return clueFragmentVectorIndex.storeFragmentVector(soupId, fragmentId, vector);
    }

    /**
     * 线索片段向量是否已存在于 Redis Stack
     */
    public boolean clueFragmentVectorExists(String soupId, String fragmentId) {
        return clueFragmentVectorIndex.hasFragmentVector(soupId, fragmentId);
    }

    /**
     * 注册线索片段到海龟汤索引集合
     */
    public void registerClueFragment(String soupId, String fragmentId) {
        String soupFragmentsKey = String.format("hai_gui:soup:%s:fragment", soupId);
        commands.sadd(soupFragmentsKey, fragmentId);
        registerSoup(soupId);
    }

    /**
     * 注册海龟汤到全局索引集合
     */
    public void registerSoup(String soupId) {
        commands.sadd("hai_gui:soups:all", soupId);
    }

    /**
     * 增加海龟汤热度分数
     * @param soupId 海龟汤ID
     * @param increment 增加的热度值
     */
    public void incrementSoupHotness(String soupId, double increment) {
        try {
            // 使用Redis的有序集合(ZSET)存储热度排行榜
            commands.zincrby("hai_gui:hotness:soups", increment, soupId);

            // 设置过期时间（比如30天）
            commands.expire("hai_gui:hotness:soups", 30 * 24 * 60 * 60);

            log.info("增加海龟汤热度: soupId={}, increment={}", soupId, increment);

        } catch (Exception e) {
            log.error("增加海龟汤热度失败: soupId={}, increment={}", soupId, increment, e);
        }
    }

    /**
     * 增加海龟汤播放次数（用于热度计算）
     * @param soupId 海龟汤ID
     */
    public void incrementSoupPlayCount(String soupId) {
        try {
            // 播放一次增加10点热度
            incrementSoupHotness(soupId, 10.0);

            // 同时更新每日播放统计
            String today = java.time.LocalDate.now().toString();
            commands.zincrby("hai_gui:daily:plays:" + today, 1.0, soupId);
            commands.expire("hai_gui:daily:plays:" + today, 7 * 24 * 60 * 60); // 7天过期

        } catch (Exception e) {
            log.error("增加海龟汤播放次数失败: soupId={}", soupId, e);
        }
    }

    /**
     * 获取热度排行榜前N名
     * @param topN 返回前N名
     * @return 海龟汤ID和热度分数的映射
     */
    public Map<String, Double> getHotnessRanking(int topN) {
        try {
            // 获取热度排行榜（从高到低）
            List<String> topSoups = commands.zrevrange("hai_gui:hotness:soups", 0, topN - 1);

            Map<String, Double> rankings = new LinkedHashMap<>();
            for (String soupId : topSoups) {
                Double score = commands.zscore("hai_gui:hotness:soups", soupId);
                if (score != null) {
                    rankings.put(soupId, score);
                }
            }

            log.info("获取热度排行榜完成: topN={}, actualCount={}", topN, rankings.size());
            return rankings;

        } catch (Exception e) {
            log.error("获取热度排行榜失败", e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 获取近期热门榜单（基于最近N天的数据）
     * @param days 最近多少天
     * @param topN 返回前N名
     * @return 热门海龟汤排行
     */
    public Map<String, Double> getRecentHotSoups(int days, int topN) {
        try {
            Map<String, Double> recentScores = new HashMap<>();

            // 累加最近N天的播放数据
            java.time.LocalDate endDate = java.time.LocalDate.now();
            java.time.LocalDate startDate = endDate.minusDays(days - 1);

            for (java.time.LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                String dateKey = "hai_gui:daily:plays:" + date.toString();
                List<String> soups = commands.zrevrange(dateKey, 0, -1);

                for (String soupId : soups) {
                    Double dailyScore = commands.zscore(dateKey, soupId);
                    if (dailyScore != null) {
                        recentScores.put(soupId, recentScores.getOrDefault(soupId, 0.0) + dailyScore);
                    }
                }
            }

            // 按分数排序并返回前N名
            return recentScores.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(topN)
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue,
                            LinkedHashMap::new
                    ));

        } catch (Exception e) {
            log.error("获取近期热门榜单失败: days={}, topN={}", days, topN, e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 更新海龟汤热度（基于多种因素）
     * @param soupId 海龟汤ID
     * @param playCount 播放次数
     * @param likeCount 点赞数
     * @param shareCount 分享数
     * @param commentCount 评论数
     */
    public void updateSoupHotness(String soupId, int playCount, int likeCount,
                                 int shareCount, int commentCount) {
        try {
            // 计算综合热度分数
            double hotnessScore = playCount * 1.0 + likeCount * 2.0 +
                                shareCount * 5.0 + commentCount * 1.5;

            commands.zadd("hai_gui:hotness:soups", hotnessScore, soupId);
            commands.expire("hai_gui:hotness:soups", 30 * 24 * 60 * 60);

            log.info("更新海龟汤热度: soupId={}, hotnessScore={}", soupId, hotnessScore);

        } catch (Exception e) {
            log.error("更新海龟汤热度失败: soupId={}", soupId, e);
        }
    }

    /**
     * 获取海龟汤的当前热度分数
     * @param soupId 海龟汤ID
     * @return 热度分数
     */
    public Double getSoupHotness(String soupId) {
        try {
            return commands.zscore("hai_gui:hotness:soups", soupId);
        } catch (Exception e) {
            log.error("获取海龟汤热度失败: soupId={}", soupId, e);
            return 0.0;
        }
    }

    /**
     * 清理过期的热度数据
     */
    public void cleanupExpiredHotnessData() {
        try {
            // 清理过期的每日播放数据
            java.time.LocalDate cutoffDate = java.time.LocalDate.now().minusDays(7);

            for (int i = 0; i < 30; i++) { // 清理30天内的数据
                java.time.LocalDate date = cutoffDate.minusDays(i);
                String dateKey = "hai_gui:daily:plays:" + date.toString();
                commands.del(dateKey);
            }

            log.info("清理过期热度数据完成");

        } catch (Exception e) {
            log.error("清理过期热度数据失败", e);
        }
    }

    /**
     * 获取海龟汤基本信息（以Map<String, String>格式返回）
     * @param soupId 海龟汤ID
     * @return 海龟汤基本信息
     */
    public Map<String, String> getSoupInfo(String soupId) {
        try {
            Map<String, String> rawData = commands.hgetall("hai_gui:soup:" + soupId);
            Map<String, String> result = new HashMap<>();

            for (Map.Entry<String, String> entry : rawData.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }

            return result;

        } catch (Exception e) {
            log.error("获取海龟汤信息失败: soupId={}", soupId, e);
            return new HashMap<>();
        }
    }

    /**
     * 检查海龟汤是否存在
     * @param soupId 海龟汤ID
     * @return 是否存在
     */
    public boolean soupExists(String soupId) {
        try {
            return commands.exists("hai_gui:soup:" + soupId) > 0;
        } catch (Exception e) {
            log.error("检查海龟汤是否存在失败: soupId={}", soupId, e);
            return false;
        }
    }

    /**
     * 获取海龟汤的播放次数
     * @param soupId 海龟汤ID
     * @return 播放次数
     */
    public Integer getSoupPlayCount(String soupId) {
        try {
            String playCountStr = commands.hget("hai_gui:soup:" + soupId, "playCount");
            if (playCountStr != null && !playCountStr.trim().isEmpty()) {
                return Integer.parseInt(playCountStr.trim());
            }
            return 0;
        } catch (Exception e) {
            log.error("获取海龟汤播放次数失败: soupId={}", soupId, e);
            return 0;
        }
    }

    /**
     * 更新海龟汤的播放次数
     * @param soupId 海龟汤ID
     * @param playCount 播放次数
     */
    public void updateSoupPlayCount(String soupId, int playCount) {
        try {
            commands.hset("hai_gui:soup:" + soupId, "playCount", String.valueOf(playCount));
        } catch (Exception e) {
            log.error("更新海龟汤播放次数失败: soupId={}, playCount={}", soupId, playCount, e);
        }
    }

    /**
     * 在指定海龟汤中搜索相似线索（RediSearch KNN）
     *
     * @param queryVector 查询向量
     * @param soupId 海龟汤ID
     * @param topK 返回前K个结果
     * @return 相似的片段ID列表及其相似度分数
     */
    public Map<String, Double> searchSimilarCluesInSoup(List<Float> queryVector, String soupId, int topK) {
        try {
            log.info("RediSearch 汤内检索: soupId={}, topK={}", soupId, topK);
            return clueFragmentVectorIndex.searchInSoup(queryVector, soupId, topK);
        } catch (Exception e) {
            log.error("在海龟汤中搜索相似片段失败: soupId={}", soupId, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 搜索相似的海龟汤片段（RediSearch KNN，soupId 为空时全局检索）
     */
    public Map<String, Double> searchSimilarClueFragments(List<Float> queryVector, String soupId, int topK) {
        try {
            log.info("RediSearch 片段检索: soupId={}, topK={}", soupId, topK);
            return clueFragmentVectorIndex.searchFragments(queryVector, soupId, topK);
        } catch (Exception e) {
            log.error("搜索相似片段失败: soupId={}", soupId, e);
            return Collections.emptyMap();
        }
    }

    public void add(String soupFragmentsKey, List<String> fragmentIdList) {
        commands.sadd(soupFragmentsKey, fragmentIdList.toArray(new String[0]));
    }
}