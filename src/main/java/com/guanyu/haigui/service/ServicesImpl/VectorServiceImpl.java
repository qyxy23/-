package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.VectorType;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.SoupClue;
import com.guanyu.haigui.pojo.model.VectorMetadata;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.SoupClueRepository;
import com.guanyu.haigui.repository.VectorMetadataRepository;
import com.guanyu.haigui.service.VectorService;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.utils.RedisStackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量服务实现类
 * 负责文本向量化、Redis存储和向量检索功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorServiceImpl implements VectorService {

    private final BgeVectorClientUtil vectorClient;
    private final RedisStackClient redisClient;
    private final VectorMetadataRepository vectorMetadataRepository;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final SoupClueRepository soupClueRepository;

    // 向量维度（根据BGE-small-zh-v1.5模型）
    private static final int VECTOR_DIMENSION = 768;

    @Override
    @Transactional
    public boolean vectorizeAndStoreSoupContext(HaiGuiSoup soup, List<SoupClue> clues) {
        try {
            log.info("开始向量化海龟汤上下文: soupId={}", soup.getSoupId());

            List<VectorMetadata> vectorMetadataList = new ArrayList<>();

            // 1. 向量化汤面
            VectorMetadata surfaceVector = vectorizeSoupSurface(soup.getSoupId(), soup.getSoupSurface());
            if (surfaceVector != null) {
                vectorMetadataList.add(surfaceVector);
            }

            // 2. 向量化汤底
            VectorMetadata bottomVector = vectorizeSoupBottom(soup.getSoupId(), soup.getSoupBottom());
            if (bottomVector != null) {
                vectorMetadataList.add(bottomVector);
            }

            // 3. 向量化主持人手册
            VectorMetadata manualVector = vectorizeHostManual(soup.getSoupId(), soup.getHostManual());
            if (manualVector != null) {
                vectorMetadataList.add(manualVector);
            }

            // 4. 批量向量化线索
            List<VectorMetadata> clueVectors = vectorizeClues(soup.getSoupId(), clues);
            vectorMetadataList.addAll(clueVectors);

            // 5. 更新海龟汤的向量键名
            soup.setSoupSurfaceVec(surfaceVector != null ? surfaceVector.getRedisKey() : null);
            soup.setSoupBottomVec(bottomVector != null ? bottomVector.getRedisKey() : null);
            haiGuiSoupRepository.save(soup);

            // 6. 更新线索的向量键名
            for (int i = 0; i < clues.size() && i < clueVectors.size(); i++) {
                SoupClue clue = clues.get(i);
                VectorMetadata clueVector = clueVectors.get(i);
                if (clueVector != null) {
                    clue.setRedisKey(clueVector.getRedisKey());
                    soupClueRepository.save(clue);
                }
            }

            log.info("海龟汤上下文向量化完成: soupId={}, 向量数量={}", soup.getSoupId(), vectorMetadataList.size());
            return true;

        } catch (Exception e) {
            log.error("向量化海龟汤上下文失败: soupId={}", soup.getSoupId(), e);
            return false;
        }
    }

    @Override
    @Transactional
    public VectorMetadata vectorizeSoupSurface(String soupId, String soupSurface) {
        try {
            log.info("开始向量化汤面: soupId={}", soupId);

            // 生成向量
            List<Float> vector = vectorizeText(soupSurface);
            if (vector == null || vector.isEmpty()) {
                log.error("汤面向量化失败: soupId={}", soupId);
                return null;
            }

            // 生成Redis键名
            String redisKey = VectorMetadata.generateRedisKey(soupId, VectorType.SURFACE, null);

            // 存储向量到Redis
            boolean redisSuccess = redisClient.storeVector(redisKey, vector);
            if (!redisSuccess) {
                log.error("汤面向量存储到Redis失败: soupId={}, redisKey={}", soupId, redisKey);
                return null;
            }

            // 保存向量元数据到数据库
            VectorMetadata metadata = new VectorMetadata(soupId, VectorType.SURFACE, redisKey, VECTOR_DIMENSION);
            VectorMetadata savedMetadata = vectorMetadataRepository.save(metadata);

            log.info("汤面向量化成功: soupId={}, vectorId={}", soupId, savedMetadata.getVectorId());
            return savedMetadata;

        } catch (Exception e) {
            log.error("向量化汤面失败: soupId={}", soupId, e);
            return null;
        }
    }

    @Override
    @Transactional
    public VectorMetadata vectorizeSoupBottom(String soupId, String soupBottom) {
        try {
            log.info("开始向量化汤底: soupId={}", soupId);

            List<Float> vector = vectorizeText(soupBottom);
            if (vector == null || vector.isEmpty()) {
                log.error("汤底向量化失败: soupId={}", soupId);
                return null;
            }

            String redisKey = VectorMetadata.generateRedisKey(soupId, VectorType.BOTTOM, null);
            boolean redisSuccess = redisClient.storeVector(redisKey, vector);
            if (!redisSuccess) {
                log.error("汤底向量存储到Redis失败: soupId={}, redisKey={}", soupId, redisKey);
                return null;
            }

            VectorMetadata metadata = new VectorMetadata(soupId, VectorType.BOTTOM, redisKey, VECTOR_DIMENSION);
            VectorMetadata savedMetadata = vectorMetadataRepository.save(metadata);

            log.info("汤底向量化成功: soupId={}, vectorId={}", soupId, savedMetadata.getVectorId());
            return savedMetadata;

        } catch (Exception e) {
            log.error("向量化汤底失败: soupId={}", soupId, e);
            return null;
        }
    }

    @Override
    @Transactional
    public VectorMetadata vectorizeHostManual(String soupId, String hostManual) {
        try {
            log.info("开始向量化主持人手册: soupId={}", soupId);

            List<Float> vector = vectorizeText(hostManual);
            if (vector == null || vector.isEmpty()) {
                log.error("主持人手册向量化失败: soupId={}", soupId);
                return null;
            }

            String redisKey = VectorMetadata.generateRedisKey(soupId, VectorType.MANUAL, null);
            boolean redisSuccess = redisClient.storeVector(redisKey, vector);
            if (!redisSuccess) {
                log.error("主持人手册向量存储到Redis失败: soupId={}, redisKey={}", soupId, redisKey);
                return null;
            }

            VectorMetadata metadata = new VectorMetadata(soupId, VectorType.MANUAL, redisKey, VECTOR_DIMENSION);
            VectorMetadata savedMetadata = vectorMetadataRepository.save(metadata);

            log.info("主持人手册向量化成功: soupId={}, vectorId={}", soupId, savedMetadata.getVectorId());
            return savedMetadata;

        } catch (Exception e) {
            log.error("向量化主持人手册失败: soupId={}", soupId, e);
            return null;
        }
    }

    @Override
    @Transactional
    public VectorMetadata vectorizeClue(String soupId, SoupClue clue) {
        try {
            log.info("开始向量化线索: soupId={}, clueId={}", soupId, clue.getClueId());

            List<Float> vector = vectorizeText(clue.getClueContent());
            if (vector == null || vector.isEmpty()) {
                log.error("线索向量化失败: soupId={}, clueId={}", soupId, clue.getClueId());
                return null;
            }

            String redisKey = VectorMetadata.generateClueRedisKey(clue.getClueId());
            boolean redisSuccess = redisClient.storeVector(redisKey, vector);
            if (!redisSuccess) {
                log.error("线索向量存储到Redis失败: soupId={}, clueId={}, redisKey={}", soupId, clue.getClueId(), redisKey);
                return null;
            }

            VectorMetadata metadata = new VectorMetadata(soupId, VectorType.CLUE, redisKey, VECTOR_DIMENSION);
            VectorMetadata savedMetadata = vectorMetadataRepository.save(metadata);

            log.info("线索向量化成功: soupId={}, clueId={}, vectorId={}", soupId, clue.getClueId(), savedMetadata.getVectorId());
            return savedMetadata;

        } catch (Exception e) {
            log.error("向量化线索失败: soupId={}, clueId={}", soupId, clue.getClueId(), e);
            return null;
        }
    }

    @Override
    @Transactional
    public List<VectorMetadata> vectorizeClues(String soupId, List<SoupClue> clues) {
        List<VectorMetadata> results = new ArrayList<>();

        for (SoupClue clue : clues) {
            VectorMetadata metadata = vectorizeClue(soupId, clue);
            if (metadata != null) {
                results.add(metadata);
            }
        }

        log.info("批量向量化线索完成: soupId={}, 总数={}, 成功数={}", soupId, clues.size(), results.size());
        return results;
    }

    @Override
    public List<Float> vectorizeQuestion(String question) {
        try {
            log.info("开始向量化玩家问题: {}", question.substring(0, Math.min(50, question.length())));

            List<Float> vector = vectorizeText(question);
            if (vector == null || vector.isEmpty()) {
                log.error("玩家问题向量化失败: {}", question);
                return Collections.emptyList();
            }

            log.info("玩家问题向量化成功，向量维度: {}", vector.size());
            return vector;

        } catch (Exception e) {
            log.error("向量化玩家问题失败: {}", question, e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Double> searchSimilarVectors(List<Float> queryVector, VectorType vectorType, int topK) {
        try {
            log.info("开始搜索相似向量: vectorType={}, topK={}", vectorType, topK);

            // 获取指定类型的所有Redis键
            List<VectorMetadata> metadataList = vectorMetadataRepository.findByVectorTypeAndIsDeletedFalse(vectorType);
            List<String> redisKeys = metadataList.stream()
                    .map(VectorMetadata::getRedisKey)
                    .collect(Collectors.toList());

            if (redisKeys.isEmpty()) {
                log.warn("未找到指定类型的向量: vectorType={}", vectorType);
                return Collections.emptyMap();
            }

            // 在Redis中搜索相似向量
            Map<String, Double> searchResults = redisClient.searchSimilarVectors(queryVector, redisKeys, topK);

            log.info("向量搜索完成: vectorType={}, 查询数量={}, 结果数量={}", vectorType, redisKeys.size(), searchResults.size());
            return searchResults;

        } catch (Exception e) {
            log.error("搜索相似向量失败: vectorType={}", vectorType, e);
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Map<String, Double>> searchSimilarVectorsWithinSoup(List<Float> queryVector, String soupId, int topK) {
        try {
            log.info("开始在海龟汤范围内搜索相似向量: soupId={}, topK={}", soupId, topK);

            // 获取指定海龟汤的所有向量元数据
            List<VectorMetadata> metadataList = vectorMetadataRepository.findBySoupIdAndIsDeletedFalse(soupId);
            if (metadataList.isEmpty()) {
                log.warn("未找到海龟汤的向量数据: soupId={}", soupId);
                return Collections.emptyMap();
            }

            // 按向量类型分组
            Map<VectorType, List<String>> typeToRedisKeys = metadataList.stream()
                    .collect(Collectors.groupingBy(
                            VectorMetadata::getVectorType,
                            Collectors.mapping(VectorMetadata::getRedisKey, Collectors.toList())
                    ));

            Map<String, Map<String, Double>> results = new HashMap<>();

            // 对每种类型的向量进行搜索
            for (Map.Entry<VectorType, List<String>> entry : typeToRedisKeys.entrySet()) {
                VectorType vectorType = entry.getKey();
                List<String> redisKeys = entry.getValue();

                if (!redisKeys.isEmpty()) {
                    Map<String, Double> typeResults = redisClient.searchSimilarVectors(queryVector, redisKeys, topK);
                    if (!typeResults.isEmpty()) {
                        results.put(vectorType.name(), typeResults);
                    }
                }
            }

            log.info("海龟汤范围内向量搜索完成: soupId={}, 向量类型数={}, 总结果数={}",
                    soupId, results.size(), results.values().stream().mapToInt(Map::size).sum());
            return results;

        } catch (Exception e) {
            log.error("在海龟汤范围内搜索相似向量失败: soupId={}", soupId, e);
            return Collections.emptyMap();
        }
    }

    @Override
    public List<VectorMetadata> getSoupVectors(String soupId) {
        try {
            return vectorMetadataRepository.findBySoupIdAndIsDeletedFalse(soupId);
        } catch (Exception e) {
            log.error("获取海龟汤向量失败: soupId={}", soupId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public VectorMetadata getSoupVector(String soupId, VectorType vectorType) {
        try {
            return vectorMetadataRepository.findBySoupIdAndVectorTypeAndIsDeletedFalse(soupId, vectorType).orElse(null);
        } catch (Exception e) {
            log.error("获取海龟汤向量失败: soupId={}, vectorType={}", soupId, vectorType, e);
            return null;
        }
    }

    @Override
    public List<Float> getVectorFromRedis(String redisKey) {
        try {
            return redisClient.getVector(redisKey);
        } catch (Exception e) {
            log.error("从Redis获取向量失败: redisKey={}", redisKey, e);
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional
    public boolean deleteSoupVectors(String soupId) {
        try {
            log.info("开始删除海龟汤向量数据: soupId={}", soupId);

            // 获取所有向量元数据
            List<VectorMetadata> metadataList = vectorMetadataRepository.findBySoupIdAndIsDeletedFalse(soupId);

            // 从Redis中删除向量数据
            for (VectorMetadata metadata : metadataList) {
                redisClient.deleteVector(metadata.getRedisKey());
            }

            // 逻辑删除向量元数据
            int deletedCount = vectorMetadataRepository.deleteBySoupId(soupId);

            log.info("海龟汤向量数据删除完成: soupId={}, 删除数量={}", soupId, deletedCount);
            return true;

        } catch (Exception e) {
            log.error("删除海龟汤向量数据失败: soupId={}", soupId, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean updateSoupVectors(HaiGuiSoup soup, List<SoupClue> clues) {
        try {
            log.info("开始更新海龟汤向量数据: soupId={}", soup.getSoupId());

            // 先删除旧的向量数据
            deleteSoupVectors(soup.getSoupId());

            // 重新生成向量数据
            return vectorizeAndStoreSoupContext(soup, clues);

        } catch (Exception e) {
            log.error("更新海龟汤向量数据失败: soupId={}", soup.getSoupId(), e);
            return false;
        }
    }

    @Override
    public boolean isSoupVectorized(String soupId) {
        try {
            List<VectorMetadata> vectors = vectorMetadataRepository.findBySoupIdAndIsDeletedFalse(soupId);
            return !vectors.isEmpty();
        } catch (Exception e) {
            log.error("检查海龟汤向量化状态失败: soupId={}", soupId, e);
            return false;
        }
    }

    @Override
    public Map<VectorType, List<ContextMatchResult>> findRelevantContext(String question, String soupId, int topK) {
        try {
            log.info("开始查找相关上下文: soupId={}, topK={}", soupId, topK);

            // 1. 向量化玩家问题
            List<Float> queryVector = vectorizeQuestion(question);
            if (queryVector.isEmpty()) {
                log.error("玩家问题向量化失败，无法查找相关上下文");
                return Collections.emptyMap();
            }

            // 2. 在海龟汤范围内搜索相似向量
            Map<String, Map<String, Double>> searchResults = searchSimilarVectorsWithinSoup(queryVector, soupId, topK);
            if (searchResults.isEmpty()) {
                log.warn("未找到相关的上下文向量");
                return Collections.emptyMap();
            }

            // 3. 构建上下文匹配结果
            Map<VectorType, List<ContextMatchResult>> contextResults = new HashMap<>();

            // 获取海龟汤和线索信息
            Optional<HaiGuiSoup> soupOpt = haiGuiSoupRepository.findById(soupId);
            List<SoupClue> clues = soupClueRepository.findBySoupIdAndIsDeletedFalse(soupId);

            // 处理每种类型的搜索结果
            for (Map.Entry<String, Map<String, Double>> typeEntry : searchResults.entrySet()) {
                String typeName = typeEntry.getKey();
                Map<String, Double> typeResults = typeEntry.getValue();

                VectorType vectorType;
                try {
                    vectorType = VectorType.valueOf(typeName);
                } catch (IllegalArgumentException e) {
                    log.warn("未知的向量类型: {}", typeName);
                    continue;
                }

                List<ContextMatchResult> matchResults = new ArrayList<>();

                for (Map.Entry<String, Double> resultEntry : typeResults.entrySet()) {
                    String redisKey = resultEntry.getKey();
                    Double similarity = resultEntry.getValue();

                    // 根据向量类型获取对应的内容
                    String content = getContextContent(vectorType, redisKey, soupOpt.orElse(null), clues);
                    String id = extractContextId(vectorType, redisKey, soupId);

                    if (content != null && id != null) {
                        matchResults.add(new ContextMatchResult(id, content, similarity, vectorType));
                    }
                }

                if (!matchResults.isEmpty()) {
                    contextResults.put(vectorType, matchResults);
                }
            }

            log.info("相关上下文查找完成: soupId={}, 上下文类型数={}", soupId, contextResults.size());
            return contextResults;

        } catch (Exception e) {
            log.error("查找相关上下文失败: soupId={}", soupId, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 根据向量类型获取上下文内容
     */
    private String getContextContent(VectorType vectorType, String redisKey, HaiGuiSoup soup, List<SoupClue> clues) {
        switch (vectorType) {
            case SURFACE:
                return soup != null ? soup.getSoupSurface() : null;
            case BOTTOM:
                return soup != null ? soup.getSoupBottom() : null;
            case MANUAL:
                return soup != null ? soup.getHostManual() : null;
            case CLUE:
                // 从Redis键中提取clueId
                String clueId = extractClueIdFromRedisKey(redisKey);
                if (clueId != null) {
                    return clues.stream()
                            .filter(clue -> clueId.equals(clue.getClueId()))
                            .map(SoupClue::getClueContent)
                            .findFirst()
                            .orElse(null);
                }
                return null;
            default:
                return null;
        }
    }

    /**
     * 根据向量类型提取上下文ID
     */
    private String extractContextId(VectorType vectorType, String redisKey, String soupId) {
        switch (vectorType) {
            case SURFACE:
            case BOTTOM:
            case MANUAL:
                return soupId;
            case CLUE:
                return extractClueIdFromRedisKey(redisKey);
            default:
                return null;
        }
    }

    /**
     * 从Redis键中提取线索ID
     */
    private String extractClueIdFromRedisKey(String redisKey) {
        if (redisKey != null && redisKey.startsWith("hai_gui:vec:clue:")) {
            return redisKey.substring("hai_gui:vec:clue:".length());
        }
        return null;
    }

    /**
     * 向量化文本的通用方法
     *
     * @param text 文本内容
     * @return 向量数据
     */
    private List<Float> vectorizeText(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                return Collections.emptyList();
            }

            SingleEncodeResponse response = vectorClient.encodeSingle(text);
            if (response == null || response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
                return Collections.emptyList();
            }

            // embeddings是List<List<Float>>，取第一个元素
            return response.getEmbeddings().get(0);

        } catch (Exception e) {
            log.error("向量化文本失败: {}", text.substring(0, Math.min(50, text.length())), e);
            return Collections.emptyList();
        }
    }
}