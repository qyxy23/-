package com.guanyu.haigui.vector;

import com.guanyu.haigui.config.VectorSearchProperties;
import com.redis.lettucemod.RedisModulesClient;
import com.redis.lettucemod.api.StatefulRedisModulesConnection;
import com.redis.lettucemod.api.sync.RedisModulesCommands;
import io.lettuce.core.RedisURI;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.search.SearchReply;
import io.lettuce.core.search.arguments.CreateArgs;
import io.lettuce.core.search.arguments.FieldArgs;
import io.lettuce.core.search.arguments.QueryDialects;
import io.lettuce.core.search.arguments.SearchArgs;
import io.lettuce.core.search.arguments.SortByArgs;
import io.lettuce.core.search.arguments.TagFieldArgs;
import io.lettuce.core.search.arguments.VectorFieldArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 线索片段 RediSearch 向量索引（Redis Stack 原生 KNN 检索）
 */
@Component
@Slf4j
public class ClueFragmentVectorIndex {

    private final VectorSearchProperties properties;

    @Value("${qingyou.redis.host}")
    private String host;

    @Value("${qingyou.redis.port}")
    private int port;

    @Value("${qingyou.redis.password}")
    private String password;

    private RedisModulesClient modulesClient;
    private StatefulRedisModulesConnection<String, byte[]> modulesConnection;
    private RedisModulesCommands<String, byte[]> moduleCommands;

    public ClueFragmentVectorIndex(VectorSearchProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(host)
                .withPort(port);
        if (password != null && !password.isEmpty()) {
            uriBuilder.withPassword(password.toCharArray());
        }
        RedisURI redisUri = uriBuilder.build();

        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        modulesClient = RedisModulesClient.create(redisUri);
        modulesConnection = modulesClient.connect(codec);
        moduleCommands = modulesConnection.sync();

        ensureIndex();
    }

    @PreDestroy
    public void destroy() {
        if (modulesConnection != null && modulesConnection.isOpen()) {
            modulesConnection.close();
        }
        if (modulesClient != null) {
            modulesClient.shutdown();
        }
    }

    /**
     * 创建 RediSearch 向量索引（已存在则跳过）
     */
    public void ensureIndex() {
        try {
            List<byte[]> indices = moduleCommands.ftList();
            if (indices != null) {
                for (byte[] indexName : indices) {
                    if (properties.getIndexName().equals(new String(indexName, StandardCharsets.UTF_8))) {
                        log.info("RediSearch 向量索引已存在: {}", properties.getIndexName());
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询 RediSearch 索引列表失败，尝试直接创建: {}", e.getMessage());
        }

        try {
            CreateArgs<String, byte[]> createArgs = CreateArgs.<String, byte[]>builder()
                    .on(CreateArgs.TargetType.HASH)
                    .withPrefix(properties.getKeyPrefix())
                    .build();

            List<FieldArgs<String>> fields = List.of(
                    TagFieldArgs.<String>builder().name("soup_id").build(),
                    TagFieldArgs.<String>builder().name("fragment_id").build(),
                    VectorFieldArgs.<String>builder()
                            .name("embedding")
                            .hnsw()
                            .type(VectorFieldArgs.VectorType.FLOAT32)
                            .dimensions(properties.getDimensions())
                            .distanceMetric(VectorFieldArgs.DistanceMetric.COSINE)
                            .build()
            );

            moduleCommands.ftCreate(properties.getIndexName(), createArgs, fields);
            log.info("RediSearch 向量索引创建成功: {}, dim={}", properties.getIndexName(), properties.getDimensions());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("already exists")) {
                log.info("RediSearch 向量索引已存在: {}", properties.getIndexName());
                return;
            }
            throw new IllegalStateException("创建 RediSearch 向量索引失败，请确认 Redis Stack 已启用 search 模块", e);
        }
    }

    /**
     * 存储线索片段向量到 Redis Stack Hash 索引
     */
    public boolean storeFragmentVector(String soupId, String fragmentId, List<? extends Number> vector) {
        if (vector == null || vector.isEmpty()) {
            log.warn("向量为空，跳过存储: soupId={}, fragmentId={}", soupId, fragmentId);
            return false;
        }
        if (vector.size() != properties.getDimensions()) {
            log.warn("向量维度不匹配: expected={}, actual={}, soupId={}, fragmentId={}",
                    properties.getDimensions(), vector.size(), soupId, fragmentId);
        }

        try {
            String key = buildVectorKey(soupId, fragmentId);
            byte[] embedding = toVectorBytes(vector);

            Map<String, byte[]> fields = new HashMap<>();
            fields.put("soup_id", soupId.getBytes(StandardCharsets.UTF_8));
            fields.put("fragment_id", fragmentId.getBytes(StandardCharsets.UTF_8));
            fields.put("embedding", embedding);
            moduleCommands.hset(key, fields);

            log.debug("线索向量已写入 Redis Stack: key={}, dim={}", key, vector.size());
            return true;
        } catch (Exception e) {
            log.error("存储线索向量失败: soupId={}, fragmentId={}", soupId, fragmentId, e);
            return false;
        }
    }

    /**
     * 在指定海龟汤内 KNN 检索相似线索
     */
    public Map<String, Double> searchInSoup(List<Float> queryVector, String soupId, int topK) {
        String query = String.format("@soup_id:{%s}=>[KNN %d @embedding $vec AS score]", escapeTag(soupId), topK);
        return executeKnnSearch(query, queryVector);
    }

    /**
     * 全局或指定海龟汤 KNN 检索相似线索
     */
    public Map<String, Double> searchFragments(List<Float> queryVector, String soupId, int topK) {
        String query;
        if (soupId != null && !soupId.isEmpty()) {
            query = String.format("@soup_id:{%s}=>[KNN %d @embedding $vec AS score]", escapeTag(soupId), topK);
        } else {
            query = String.format("*=>[KNN %d @embedding $vec AS score]", topK);
        }
        return executeKnnSearch(query, queryVector);
    }

    /**
     * 删除单个线索向量
     */
    public void deleteFragmentVector(String soupId, String fragmentId) {
        moduleCommands.del(buildVectorKey(soupId, fragmentId));
        moduleCommands.del(buildLegacyVectorKey(soupId, fragmentId));
    }

    /**
     * 删除海龟汤下所有线索向量
     */
    public void deleteSoupFragmentVectors(String soupId, Iterable<String> fragmentIds) {
        List<String> keys = new ArrayList<>();
        for (String fragmentId : fragmentIds) {
            keys.add(buildVectorKey(soupId, fragmentId));
            keys.add(buildLegacyVectorKey(soupId, fragmentId));
        }
        if (!keys.isEmpty()) {
            moduleCommands.del(keys.toArray(new String[0]));
        }
    }

    public String buildVectorKey(String soupId, String fragmentId) {
        return properties.getKeyPrefix() + soupId + ":" + fragmentId;
    }

    /**
     * 检查线索向量是否已在 Redis Stack 中（Hash 含 embedding 字段）
     */
    public boolean hasFragmentVector(String soupId, String fragmentId) {
        try {
            Boolean exists = moduleCommands.hexists(buildVectorKey(soupId, fragmentId), "embedding");
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("检查线索向量是否存在失败: soupId={}, fragmentId={}", soupId, fragmentId, e);
            return false;
        }
    }

    private String buildLegacyVectorKey(String soupId, String fragmentId) {
        return String.format("hai_gui:soup:%s:fragment:%s", soupId, fragmentId);
    }

    private Map<String, Double> executeKnnSearch(String query, List<Float> queryVector) {
        Map<String, Double> results = new LinkedHashMap<>();
        if (queryVector == null || queryVector.isEmpty()) {
            return results;
        }

        try {
            byte[] queryBytes = toVectorBytes(queryVector);
            SearchArgs<String, byte[]> searchArgs = SearchArgs.<String, byte[]>builder()
                    .returnField("fragment_id")
                    .returnField("score")
                    .sortBy(SortByArgs.<String>builder().attribute("score").build())
                    .param("vec", queryBytes)
                    .dialect(QueryDialects.DIALECT2)
                    .build();

            SearchReply<String, byte[]> reply = moduleCommands.ftSearch(
                    properties.getIndexName(),
                    query.getBytes(StandardCharsets.UTF_8),
                    searchArgs);
            if (reply == null || reply.getResults() == null) {
                return results;
            }

            for (SearchReply.SearchResult<String, byte[]> result : reply.getResults()) {
                String fragmentId = extractField(result, "fragment_id");
                String scoreText = extractField(result, "score");
                if (fragmentId == null) {
                    continue;
                }
                double distance = scoreText != null ? Double.parseDouble(scoreText) : 0.0;
                double similarity = cosineDistanceToSimilarity(distance);
                results.put(fragmentId, similarity);
            }

            log.info("RediSearch KNN 检索完成: matched={}", results.size());
            return results;
        } catch (Exception e) {
            log.error("RediSearch KNN 检索失败: query={}", query, e);
            return results;
        }
    }

    private String extractField(SearchReply.SearchResult<String, byte[]> result, String fieldName) {
        if (result.getFields() == null) {
            return null;
        }
        byte[] value = result.getFields().get(fieldName);
        return value != null ? new String(value, StandardCharsets.UTF_8) : null;
    }

    /**
     * Redis COSINE 距离转余弦相似度（BGE 向量已归一化）
     */
    private double cosineDistanceToSimilarity(double distance) {
        return Math.max(0.0, 1.0 - distance);
    }

    private byte[] toVectorBytes(List<? extends Number> vector) {
        float[] floats = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            floats[i] = vector.get(i).floatValue();
        }
        return floatsToBytes(floats);
    }

    private byte[] floatsToBytes(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(floats);
        return buffer.array();
    }

    /**
     * TAG 查询转义：UUID 等含 {@code -} 的值在 {@code {...}} 内须转义，否则 {@code -4ff6} 会被解析为运算符
     */
    private String escapeTag(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\', '-', '.', ':', ';', '{', '}', '[', ']', '(', ')', '|', ',', '"', '\'', '!', '@', '#', '$', '%', '^', '&', '*', '+', '=', '~', ' ', '<', '>' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
