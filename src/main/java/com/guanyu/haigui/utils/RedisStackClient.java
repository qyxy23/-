package com.guanyu.haigui.utils;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RedisStackClient {

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
     * 获取海龟汤向量
     * @param soupId 海龟汤ID
     * @param vectorType 向量类型
     * @return 向量数据
     */
    public List<Float> getSoupVector(String soupId, String vectorType) {
        try {
            String vectorKey = String.format("hai_gui:vec:%s:%s", vectorType.toLowerCase(), soupId);
            String vectorJson = commands.get(vectorKey);

            if (vectorJson == null) {
                return null;
            }

            // 解析JSON数组格式的向量
            String vectorStr = vectorJson.replaceAll("[\\[\\]]", "");
            String[] vectorParts = vectorStr.split(",\\s*");

            List<Float> vector = new ArrayList<>();
            for (String part : vectorParts) {
                if (!part.trim().isEmpty()) {
                    vector.add(Float.parseFloat(part.trim()));
                }
            }

            return vector;

        } catch (Exception e) {
            log.error("获取海龟汤向量失败: soupId={}, vectorType={}", soupId, vectorType, e);
            return null;
        }
    }




    /**
     * 计算两个向量的余弦相似度
     */
    private double calculateCosineSimilarity(List<Float> vector1, List<Float> vector2) {
        if (vector1.size() != vector2.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vector1.size(); i++) {
            dotProduct += vector1.get(i) * vector2.get(i);
            norm1 += Math.pow(vector1.get(i), 2);
            norm2 += Math.pow(vector2.get(i), 2);
        }

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 删除海龟汤的所有相关数据（包括向量）
     * @param soupId 海龟汤ID
     */
    public void deleteSoup(String soupId) {
        try {

            // 删除基本信息
            commands.del("hai_gui:soup:" + soupId);

            // 删除向量数据
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
     * 将浮点数向量编码为 Base64 字符串（存储/传输格式）
     */
    /**
     * 存储向量到Redis
     *
     * @param redisKey Redis键名
     * @param vector 向量数据
     * @return 是否成功
     */
    public boolean storeVector(String redisKey, List<Double> vector) {
        try {
            if (vector == null || vector.isEmpty()) {
                log.warn("向量为空，无法存储: redisKey={}", redisKey);
                return false;
            }

            // 将向量转换为JSON字符串存储
            String vectorJson = vector.toString();
            commands.set(redisKey, vectorJson);

            // 设置过期时间（30天）
            commands.expire(redisKey, 30 * 24 * 60 * 60);

            log.info("成功存储向量: key={}, dimension={}", redisKey, vector.size());
            return true;

        } catch (Exception e) {
            log.error("存储向量失败: key={}", redisKey, e);
            return false;
        }
    }

    /**
     * 从Redis获取向量
     *
     * @param redisKey Redis键名
     * @return 向量数据
     */
    public List<Float> getVector(String redisKey) {
        try {
            String vectorJson = commands.get(redisKey);
            if (vectorJson == null) {
                log.warn("向量不存在: key={}", redisKey);
                return Collections.emptyList();
            }

            // 解析JSON格式的向量
            String vectorStr = vectorJson.replaceAll("[\\[\\]]", "");
            String[] vectorParts = vectorStr.split(",\\s*");

            List<Float> vector = new ArrayList<>();
            for (String part : vectorParts) {
                if (!part.trim().isEmpty()) {
                    vector.add(Float.parseFloat(part.trim()));
                }
            }

            log.debug("成功获取向量: key={}, dimension={}", redisKey, vector.size());
            return vector;

        } catch (Exception e) {
            log.error("获取向量失败: key={}", redisKey, e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除向量
     *
     * @param redisKey Redis键名
     * @return 是否成功
     */
    public boolean deleteVector(String redisKey) {
        try {
            Long result = commands.del(redisKey);
            boolean success = result != null && result > 0;

            if (success) {
                log.info("成功删除向量: key={}", redisKey);
            } else {
                log.warn("向量不存在，删除失败: key={}", redisKey);
            }

            return success;

        } catch (Exception e) {
            log.error("删除向量失败: key={}", redisKey, e);
            return false;
        }
    }

    /**
     * 在多个向量中搜索相似的向量
     *
     * @param queryVector 查询向量
     * @param redisKeys 要搜索的Redis键名列表
     * @param topK 返回前K个结果
     * @return 匹配结果（键名 -> 相似度分数）
     */
    public Map<String, Double> searchSimilarVectors(List<Float> queryVector, List<String> redisKeys, int topK) {
        try {
            Map<String, Double> results = new HashMap<>();

            for (String redisKey : redisKeys) {
                List<Float> storedVector = getVector(redisKey);
                if (storedVector != null && !storedVector.isEmpty()) {
                    double similarity = calculateCosineSimilarity(queryVector, storedVector);
                    if (similarity > 0.0) { // 只保留有相似度的结果
                        results.put(redisKey, similarity);
                    }
                }
            }

            // 按相似度排序并取前topK个结果
            return results.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(topK)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue,
                            LinkedHashMap::new
                    ));

        } catch (Exception e) {
            log.error("搜索相似向量失败", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 在指定海龟汤中搜索相似线索（基于Redis中的向量）
     *
     * @param queryVector 查询向量
     * @param soupId 海龟汤ID
     * @param topK 返回前K个结果
     * @return 相似的片段ID列表及其相似度分数
     */
    public Map<String, Double> searchSimilarCluesInSoup(List<Float> queryVector, String soupId, int topK) {
        try {
            Map<String, Double> results = new HashMap<>();
            List<String> fragmentKeys = new ArrayList<>();

            // 获取指定海龟汤的所有片段
            String soupFragmentsKey = String.format("hai_gui:soup:%s:fragment", soupId);
            Set<String> fragmentIds = commands.smembers(soupFragmentsKey);

            if (fragmentIds.isEmpty()) {
                log.warn("海龟汤中没有找到片段: soupId={}", soupId);
                return results;
            }

            // 构建包含海龟汤ID的向量键
            for (String fragmentId : fragmentIds) {
                String soupFragmentKey = String.format("hai_gui:soup:%s:fragment:%s", soupId, fragmentId);
                fragmentKeys.add(soupFragmentKey);
            }

            log.info("在海龟汤中搜索片段: soupId={}, fragmentCount={}", soupId, fragmentKeys.size());

            // 搜索相似向量
            Map<String, Double> searchResults = searchSimilarVectors(queryVector, fragmentKeys, topK);

            // 转换键名，只保留片段ID
            for (Map.Entry<String, Double> entry : searchResults.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(String.format("hai_gui:soup:%s:fragment:", soupId))) {
                    String fragmentId = key.substring(String.format("hai_gui:soup:%s:fragment:", soupId).length());
                    results.put(fragmentId, entry.getValue());
                } else {
                    results.put(key, entry.getValue());
                }
            }

            log.info("海龟汤内片段搜索完成: soupId={}, totalFragments={}, matchedFragments={}",
                    soupId, fragmentKeys.size(), results.size());

            return results;

        } catch (Exception e) {
            log.error("在海龟汤中搜索相似片段失败: soupId={}", soupId, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 搜索相似的海龟汤片段（基于Redis中的向量）
     *
     * @param queryVector 查询向量
     * @param soupId 海龟汤ID（可选，如果提供则只在该汤内搜索）
     * @param topK 返回前K个结果
     * @return 相似的片段ID列表及其相似度分数
     */
    public Map<String, Double> searchSimilarClueFragments(List<Float> queryVector, String soupId, int topK) {
        try {
            Map<String, Double> results = new HashMap<>();
            List<String> fragmentKeys = new ArrayList<>();

            if (soupId != null && !soupId.isEmpty()) {
                // 搜索特定海龟汤的片段
                String soupFragmentsKey = String.format("hai_gui:soup:%s:fragment", soupId);
                Set<String> fragmentIds = commands.smembers(soupFragmentsKey);

                for (String fragmentId : fragmentIds) {
                    fragmentKeys.add(String.format("hai_gui:fragment:%s", fragmentId));
                }

                log.info("搜索特定海龟汤的片段: soupId={}, fragmentCount={}", soupId, fragmentKeys.size());
            } else {
                // 搜索所有海龟汤的片段
                Set<String> soupIds = commands.smembers("hai_gui:soups:all");

                for (String id : soupIds) {
                    String soupFragmentsKey = String.format("hai_gui:soup:%s:fragment", id);
                    Set<String> fragmentIds = commands.smembers(soupFragmentsKey);

                    for (String fragmentId : fragmentIds) {
                        fragmentKeys.add(String.format("hai_gui:fragment:%s", fragmentId));
                    }
                }

                log.info("搜索所有海龟汤的片段: soupCount={}, totalFragmentCount={}", soupIds.size(), fragmentKeys.size());
            }

            if (fragmentKeys.isEmpty()) {
                log.warn("没有找到任何片段向量");
                return results;
            }

            // 使用现有的向量搜索功能
            Map<String, Double> searchResults = searchSimilarVectors(queryVector, fragmentKeys, topK);

            // 转换键名，去掉前缀，只保留片段ID
            for (Map.Entry<String, Double> entry : searchResults.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("hai_gui:fragment:")) {
                    String fragmentId = key.substring("hai_gui:fragment:".length());
                    results.put(fragmentId, entry.getValue());
                } else {
                    results.put(key, entry.getValue());
                }
            }

            log.info("片段向量搜索完成: queryType={}, totalFragments={}, matchedFragments={}",
                    soupId != null ? "汤内" : "全局", fragmentKeys.size(), results.size());

            return results;

        } catch (Exception e) {
            log.error("搜索相似片段失败: soupId={}", soupId, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 批量删除向量
     *
     * @param redisKeys Redis键名列表
     * @return 删除的数量
     */
    public int deleteVectors(List<String> redisKeys) {
        try {
            if (redisKeys == null || redisKeys.isEmpty()) {
                return 0;
            }

            String[] keyArray = redisKeys.toArray(new String[0]);
            Long deletedCount = commands.del(keyArray);

            int count = deletedCount != null ? deletedCount.intValue() : 0;
            log.info("批量删除向量完成: 删除数量={}", count);
            return count;

        } catch (Exception e) {
            log.error("批量删除向量失败", e);
            return 0;
        }
    }

    /**
     * 检查向量是否存在
     *
     * @param redisKey Redis键名
     * @return 是否存在
     */
    public boolean vectorExists(String redisKey) {
        try {
            Long exists = commands.exists(redisKey);
            return exists != null && exists > 0;

        } catch (Exception e) {
            log.error("检查向量存在性失败: key={}", redisKey, e);
            return false;
        }
    }

    private String encodeVectorToBase64(List<Float> vector) {
        float[] floatArray = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            floatArray[i] = vector.get(i);
        }
        ByteBuffer buffer = ByteBuffer.allocate(floatArray.length * 4); // Float 占 4 字节
        buffer.asFloatBuffer().put(floatArray);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    public void add(String soupFragmentsKey, List<String> fragmentIdList) {
        commands.sadd(soupFragmentsKey, fragmentIdList.toArray(new String[0]));
    }
}