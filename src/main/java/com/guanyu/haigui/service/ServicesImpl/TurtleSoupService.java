package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.dto.CreateHaiGuiSoupDTO;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.vo.ClueMatchResult;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.pojo.vo.SoupRankInfo;
import com.guanyu.haigui.repository.ClueFragmentRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.InferenceTaskRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.VectorService;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import com.guanyu.haigui.utils.SoupJsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 海龟汤服务实现类（重构版）
 * 集成向量化功能，提供智能搜索和推荐
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TurtleSoupService {

    private final VectorService vectorService;
    private final HaiGuiVectorService haiGuiVectorService;
    private final RedisStackClient redisClient;
    private final BgeVectorClientUtil vectorClient;
    private final UserInfoRepository userInfoRepository;
    private final HaiGuiRankingService haiGuiRankingService;
    private final SoupJsonParser soupJsonParser;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final ClueFragmentRepository clueFragmentRepository;
    private final InferenceTaskRepository inferenceTaskRepository;
    private final ClueDecompositionService clueDecompositionService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final boolean debugMode = false;

    @Autowired
    private AIManager aiManager;

    /**
     * 新增海龟汤（包含向量化处理和智能线索解析）
     *
     * @param soup 海龟汤对象
     * @return 是否成功
     */
    @Transactional
    public boolean addTurtleSoup(CreateHaiGuiSoupDTO soup) {
        try {
            // 创建海龟汤实体
            HaiGuiSoup haiGuiSoup = new HaiGuiSoup();
            haiGuiSoup.setSoupId(java.util.UUID.randomUUID().toString());
            haiGuiSoup.setPlayCount(0);
            haiGuiSoup.setUploadTime(java.time.LocalDateTime.now());

            // 显式设置创建时间（确保@CreationTimestamp正常工作）
            haiGuiSoup.setCreatedAt(java.time.LocalDateTime.now());
            haiGuiSoup.setUpdatedAt(java.time.LocalDateTime.now());

            // 复制基本属性
            haiGuiSoup.setSoupTitle(soup.getSoupTitle());
            haiGuiSoup.setSoupSurface(soup.getSoupSurface());
            haiGuiSoup.setSoupBottom(soup.getSoupBottom());
            haiGuiSoup.setHostManual(soup.getHostManual());

            // 解析线索和进度设置
            String keyCluesInput = soup.getKeyCluesAsString();
            String progressSettingsInput = soup.getProgressSettingsAsString();

            log.info("原始keyClues输入类型: {}, 值: '{}'",
                    soup.getKeyClues() != null ? soup.getKeyClues().getClass().getSimpleName() : "null",
                    keyCluesInput);

            // 解析线索列表（使用GameClue业务实体）
            List<GameClue> clues = soupJsonParser.parseKeyClues(keyCluesInput);

            log.info("解析得到的线索数量: {}", clues.size());

            UserInfo userInfo = userInfoRepository.findById(BaseContext.getCurrentId())
                    .orElseThrow(() -> new BusinessException(404, "用户不存在"));
            haiGuiSoup.setUploaderId(userInfo.getUserId());
            haiGuiSoup.setCreatorId(userInfo.getUserId());
            haiGuiSoup.setUploader(userInfo);
            haiGuiSoup.setCreator(userInfo);

            // 注意：progress_settings现在存储在haigui_soup_progress_task表中
            // 生成混合方案的内容

            // 先设置一个空的线索ID列表，满足NOT NULL约束
            haiGuiSoup.setKeyClues("[]");

            log.info("开始新增海龟汤: title={}", soup.getSoupTitle());

            // 1. 先保存海龟汤到数据库（确保外键引用存在）
            HaiGuiSoup savedSoup = haiGuiSoupRepository.save(haiGuiSoup);

            log.info("海龟汤保存到数据库成功: soupId={}", savedSoup.getSoupId());

            // 2. 使用AI拆解汤底并分析用户线索（合并处理）
            List<ClueFragment> aiFragments = new ArrayList<>();
            try {
                log.info("开始为海龟汤生成AI拆解的线索片段，同时分析用户线索，用户线索数量: {}", clues.size());

                // 使用ClueDecompositionService拆解汤底并分析用户线索
                aiFragments = clueDecompositionService.decomposeSoupBottomWithUserClues(
                    savedSoup.getSoupTitle(),
                    savedSoup.getSoupSurface(),
                    savedSoup.getSoupBottom(),
                    clues
                );

                // 3. 保存AI生成的线索片段到数据库并存储到Redis
                for (ClueFragment fragment : aiFragments) {
                    fragment.setSoupId(savedSoup.getSoupId());
                    // 确保fragmentId为null，让JPA自动生成
                    fragment.setFragmentId(null);
                    ClueFragment savedFragment = clueFragmentRepository.save(fragment);

                    // 将AI生成的线索片段向量存储到Redis
                    try {
                        // 检查片段是否已有向量数据
                        if (savedFragment.getVectorData() != null && !savedFragment.getVectorData().isEmpty() && savedFragment.getFragmentId() != null) {
                            // 新键结构：包含海龟汤ID和片段ID
                            String soupFragmentKey = String.format("hai_gui:soup:%s:fragment:%s", savedSoup.getSoupId(), savedFragment.getFragmentId());
                            List<Float> floatVector = savedFragment.getVectorData().stream()
                                    .map(Double::floatValue)
                                    .collect(java.util.stream.Collectors.toList());
                            redisClient.storeVector(soupFragmentKey, floatVector);


                            // 将片段ID添加到海龟汤的片段集合中
                            String soupFragmentsKey = String.format("hai_gui:soup:%s:fragments", savedSoup.getSoupId());
                            redisClient.getCommands().sadd(soupFragmentsKey, savedFragment.getFragmentId().toString());

                            // 将海龟汤ID添加到所有汤的集合中
                            redisClient.getCommands().sadd("hai_gui:soups:all", savedSoup.getSoupId());

                            // 在片段键中存储元数据
                            String metadataKey = String.format("hai_gui:fragment:%s:meta", savedFragment.getFragmentId());
                            String metadata = String.format("{\"soupId\":\"%s\",\"content\":\"%s\",\"type\":\"%s\",\"isCore\":%s,\"source\":\"AI\"}",
                                    savedSoup.getSoupId(),
                                    savedFragment.getFragmentContent().length() > 50 ? savedFragment.getFragmentContent().substring(0, 50) + "..." : savedFragment.getFragmentContent(),
                                    savedFragment.getFragmentType(),
                                    savedFragment.getIsCoreClue());
                            redisClient.getCommands().set(metadataKey, metadata);

                            log.info("AI线索片段向量存储到Redis成功: soupId={}, fragmentId={}, dimension={}",
                                    savedSoup.getSoupId(), savedFragment.getFragmentId(), floatVector.size());
                        } else {
                            log.warn("AI线索片段缺少向量数据或ID: fragmentId={}, hasVector={}",
                                    savedFragment.getFragmentId(),
                                    savedFragment.getVectorData() != null ? "null" : "notEmpty");
                        }
                    } catch (Exception redisEx) {
                        log.warn("AI线索片段向量存储到Redis失败: fragmentId={}, error={}",
                                savedFragment.getFragmentId(), redisEx.getMessage());
                        // Redis存储失败不影响MySQL存储
                    }
                }

                log.info("AI拆解线索片段完成，生成{}个片段", aiFragments.size());

            } catch (Exception e) {
                log.warn("AI拆解线索片段失败: {}", e.getMessage());
                // AI拆解失败时，仍然生成推理任务
                generateInferenceTasksForSoup(savedSoup.getSoupId());
            }


            // 3. 更新海龟汤的关键线索ID列表和拆解配置
            updateSoupWithClueInfo(savedSoup, aiFragments);
            savedSoup.setUpdatedAt(java.time.LocalDateTime.now());
            haiGuiSoupRepository.save(savedSoup);

            log.info("海龟汤混合方案内容生成完成: soupId={}", savedSoup.getSoupId());

            // 4. 确认向量化处理已完成（ClueDecompositionService中已包含向量化）
            try {
                List<ClueFragment> savedFragments = clueFragmentRepository.findBySoupIdAndIsDeletedFalse(savedSoup.getSoupId());
                log.info("获取到已向量化的线索片段数量: {}", savedFragments.size());

                // 验证向量数据
                long vectorizedCount = savedFragments.stream()
                    .filter(f -> f.getVectorData() != null && !f.getVectorData().isEmpty())
                    .count();
                log.info("成功向量化的片段数量: {}", vectorizedCount);

            } catch (Exception e) {
                log.warn("确认向量化处理失败: {}", e.getMessage());
            }
            log.info("海龟汤新增成功: soupId={}", savedSoup.getSoupId());
            return true;

        } catch (Exception e) {
            log.error("新增海龟汤失败: title={}", soup.getSoupTitle(), e);
            throw new BusinessException(500, "新增海龟汤失败: " + e.getMessage());
        }
    }

    /**
     * 在指定海龟汤中搜索相关线索（基于向量匹配）
     *
     * @param question 玩家输入的问题
     * @param soupId   海龟汤ID
     * @param topK     返回前K个最相关的线索
     * @return 匹配的线索信息（包含线索内容、相似度等）
     */
    public List<ClueMatchResult> findMatchingCluesInSoup(String question, String soupId, int topK) {
        try {
            log.info("在海龟汤中搜索相关线索: question={}, soupId={}, topK={}", question, soupId, topK);

            // 1. 将问题向量化
            SingleEncodeResponse response = BgeVectorClientUtil.encodeSingle(question);
            List<Float> queryVector = response.getEmbeddings().get(0);

            // 2. 使用Redis在指定海龟汤中搜索相似片段
            Map<String, Double> fragmentResults = redisClient.searchSimilarCluesInSoup(
                    queryVector, soupId, topK * 2); // 搜索更多，后续过滤

            // 3. 获取片段详细信息
            List<ClueMatchResult> results = new ArrayList<>();
            for (Map.Entry<String, Double> entry : fragmentResults.entrySet()) {
                String fragmentId = entry.getKey();
                Double similarity = entry.getValue();

                try {
                    Long fragId = Long.parseLong(fragmentId);
                    ClueFragment fragment = clueFragmentRepository.findById(fragId).orElse(null);
                    if (fragment != null) {
                        ClueMatchResult result = new ClueMatchResult();
                        result.setFragmentId(fragmentId);
                        result.setFragmentContent(fragment.getFragmentContent());
                        result.setFragmentType(fragment.getFragmentType());
                        result.setIsCoreClue(fragment.getIsCoreClue());
                        result.setInferenceLevel(fragment.getInferenceLevel());
                        result.setSimilarity(similarity);
                        result.setSoupId(fragment.getSoupId());

                        // 计算匹配原因
                        String matchReason = generateMatchReason(question, fragment.getFragmentContent(), similarity);
                        result.setMatchReason(matchReason);

                        results.add(result);
                    }
                } catch (Exception ex) {
                    log.warn("解析片段ID失败: fragmentId={}, error={}", fragmentId, ex.getMessage());
                }
            }

            // 4. 按相似度排序并限制数量
            results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
            if (results.size() > topK) {
                results = results.subList(0, topK);
            }

            log.info("海龟汤内线索搜索完成: soupId={}, totalFragments={}, matchedClues={}",
                    soupId, fragmentResults.size(), results.size());

            return results;

        } catch (Exception e) {
            log.error("在海龟汤中搜索相关线索失败: question={}, soupId={}", question, soupId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 生成匹配原因说明
     */
    private String generateMatchReason(String question, String fragmentContent, double similarity) {
        if (similarity > 0.8) {
            return "高度相关，与问题非常匹配";
        } else if (similarity > 0.6) {
            return "中度相关，可能包含答案线索";
        } else if (similarity > 0.4) {
            return "低度相关，需要进一步推理";
        } else {
            return "弱相关，仅供参考";
        }
    }

    /**
     * 根据玩家问题检索相似海龟汤（向量搜索）
     *
     * @param question 玩家输入的问题
     * @param topK     返回前K个结果
     * @return 匹配的海龟汤ID及其相似度分数
     */
    public Map<String, Double> findMatchingSoup(String question, int topK) {
        try {
            log.info("开始搜索相似海龟汤: question={}, topK={}", question, topK);

            // 优先使用Redis向量搜索，如果失败则使用原有方法
            try {
                return findMatchingSoupWithRedis(question, topK);
            } catch (Exception redisEx) {
                log.warn("Redis向量搜索失败，使用原有方法: {}", redisEx.getMessage());
                return haiGuiVectorService.searchSimilarSoups(question, topK);
            }

        } catch (Exception e) {
            log.error("搜索相似海龟汤失败: question={}", question, e);
            return Map.of();
        }
    }

    /**
     * 使用Redis搜索相似海龟汤（基于片段向量）
     *
     * @param question 玩家输入的问题
     * @param topK     返回前K个结果
     * @return 匹配的海龟汤ID及其相似度分数
     */
    public Map<String, Double> findMatchingSoupWithRedis(String question, int topK) {
        try {
            log.info("使用Redis搜索相似海龟汤: question={}, topK={}", question, topK);

            // 1. 将问题向量化
            com.guanyu.haigui.pojo.vo.SingleEncodeResponse response = vectorClient.encodeSingle(question);
            List<Float> queryVector = response.getEmbeddings().get(0);

            // 2. 优先使用新的搜索方法（全局搜索）
            Map<String, Double> fragmentResults = redisClient.searchSimilarClueFragments(
                    queryVector, null, topK * 3); // 搜索更多片段，后续按海龟汤聚合

            // 3. 将片段相似度聚合为海龟汤相似度
            Map<String, List<Double>> soupSimilarities = new HashMap<>();
            for (Map.Entry<String, Double> entry : fragmentResults.entrySet()) {
                String fragmentId = entry.getKey();
                Double similarity = entry.getValue();

                // 根据片段ID查找海龟汤ID（使用Redis元数据或数据库查询）
                String soupId = null;
                try {
                    // 首先尝试从Redis元数据获取
                    String metadataKey = String.format("hai_gui:fragment:%s:meta", fragmentId);
                    String metadata = redisClient.getCommands().get(metadataKey);
                    if (metadata != null && !metadata.isEmpty()) {
                        // 从JSON中解析soupId
                        soupId = extractSoupIdFromMetadata(metadata);
                    }

                    // 如果Redis中没有，则从数据库查询
                    if (soupId == null) {
                        Long fragId = Long.parseLong(fragmentId);
                        ClueFragment fragment = clueFragmentRepository.findById(fragId).orElse(null);
                        if (fragment != null && fragment.getSoupId() != null) {
                            soupId = fragment.getSoupId();
                        }
                    }

                    if (soupId != null) {
                        soupSimilarities.computeIfAbsent(soupId, k -> new ArrayList<>()).add(similarity);
                    }
                } catch (Exception ex) {
                    log.warn("解析片段ID失败: fragmentId={}, error={}", fragmentId, ex.getMessage());
                }
            }

            // 4. 计算每个海龟汤的综合相似度
            Map<String, Double> results = new HashMap<>();
            for (Map.Entry<String, List<Double>> entry : soupSimilarities.entrySet()) {
                String soupId = entry.getKey();
                List<Double> similarities = entry.getValue();

                // 使用加权平均：最高相似度权重更大，片段数量也有影响
                double maxSimilarity = similarities.stream()
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(0.0);

                double avgSimilarity = similarities.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

                // 综合相似度：最高相似度 * 0.7 + 平均相似度 * 0.3
                double combinedSimilarity = maxSimilarity * 0.7 + avgSimilarity * 0.3;

                results.put(soupId, combinedSimilarity);
            }

            // 5. 按相似度排序并返回topK结果
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
            log.error("Redis搜索相似海龟汤失败: question={}", question, e);
            throw e; // 重新抛出异常，让上层方法使用备选方案
        }
    }

    /**
     * 从元数据中提取海龟汤ID
     */
    private String extractSoupIdFromMetadata(String metadata) {
        try {
            // 简单的JSON解析，提取soupId
            int soupIdIndex = metadata.indexOf("\"soupId\":\"");
            if (soupIdIndex >= 0) {
                int start = soupIdIndex + 10; // "\"soupId\":\"".length()
                int end = metadata.indexOf("\"", start);
                if (end > start) {
                    return metadata.substring(start, end);
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("解析元数据失败: metadata={}", metadata, e);
            return null;
        }
    }

    /**
     * 更新海龟汤信息（包含向量更新）
     *
     * @param soup 更新后的海龟汤对象
     * @return 是否成功
     */
    public boolean updateTurtleSoup(HaiGuiSoup soup) {
        try {
            log.info("开始更新海龟汤: soupId={}", soup.getSoupId());

            // 获取关联的线索
            // 使用新的线索片段表
            List<ClueFragment> fragments = clueFragmentRepository.findBySoupIdAndIsDeletedFalse(soup.getSoupId());
            // 注意：vectorizeAndStoreSoupContext方法需要适配新的数据结构
            // boolean vectorSuccess = vectorService.vectorizeAndStoreSoupContext(soup, fragments);

            // 使用新的向量服务更新向量数据
            log.info("向量更新功能暂时跳过: soupId={}", soup.getSoupId());

            log.info("海龟汤更新成功: soupId={}", soup.getSoupId());
            return true;

        } catch (Exception e) {
            log.error("更新海龟汤失败: soupId={}", soup.getSoupId(), e);
            return false;
        }
    }

    /**
     * 删除海龟汤（包含向量数据清理）
     *
     * @param soupId 海龟汤ID
     * @return 是否成功
     */
    public boolean deleteTurtleSoup(String soupId) {
        try {
            log.info("开始删除海龟汤: soupId={}", soupId);

            // 使用新的向量服务删除向量数据
            boolean deleteSuccess = vectorService.deleteSoupVectors(soupId);
            if (!deleteSuccess) {
                log.error("删除海龟汤向量数据失败: soupId={}", soupId);
                // 向量删除失败不影响整体删除流程
                log.warn("海龟汤删除成功但向量清理失败: soupId={}", soupId);
            }

            log.info("海龟汤删除成功: soupId={}", soupId);
            return true;

        } catch (Exception e) {
            log.error("删除海龟汤失败: soupId={}", soupId, e);
            return false;
        }
    }

    /**
     * 批量向量化现有海龟汤
     *
     * @param soups 海龟汤列表
     * @return 成功数量
     */
    public int batchVectorizeSoups(List<HaiGuiSoup> soups) {
        log.info("开始批量向量化海龟汤: count={}", soups.size());
        return haiGuiVectorService.batchVectorizeSoups(soups);
    }

    /**
     * 获取海龟汤推荐（基于向量相似度）
     *
     * @param soupId 基准海龟汤ID
     * @param topK   推荐数量
     * @return 推荐的海龟汤ID及其相似度分数
     */
    public Map<String, Double> recommendSoups(String soupId, int topK) {
        try {
            log.info("开始获取海龟汤推荐: soupId={}, topK={}", soupId, topK);

            // 获取基准海龟汤的汤面向量
            List<Float> soupVector = haiGuiVectorService.getSoupVector(soupId, "SURFACE");
            if (soupVector == null || soupVector.isEmpty()) {
                log.error("获取基准海龟汤向量失败: soupId={}", soupId);
                return Map.of();
            }

            // 基于向量搜索推荐相似海龟汤
            return redisClient.searchSimilarSoups(soupVector, "SURFACE", topK);

        } catch (Exception e) {
            log.error("获取海龟汤推荐失败: soupId={}", soupId, e);
            return Map.of();
        }
    }

    /**
     * 检查海龟汤是否已向量化
     *
     * @param soupId 海龟汤ID
     * @return 是否已向量化
     */
    public boolean isSoupVectorized(String soupId) {
        try {
            return vectorService.isSoupVectorized(soupId);

        } catch (Exception e) {
            log.error("检查海龟汤向量化状态失败: soupId={}", soupId, e);
            return false;
        }
    }

    /**
     * 记录用户玩海龟汤的行为（自动记录播放行为）
     *
     * @param soupId 海龟汤ID
     */
    public void recordPlayAction(String soupId) {
        try {
            Long userId = BaseContext.getCurrentId();
            haiGuiRankingService.recordUserAction(soupId, userId, "play");
            log.info("记录用户播放行为: userId={}, soupId={}", userId, soupId);

        } catch (Exception e) {
            log.error("记录用户播放行为失败: soupId={}", soupId, e);
        }
    }

    /**
     * 记录用户点赞行为
     *
     * @param soupId 海龟汤ID
     */
    public void recordLikeAction(String soupId) {
        try {
            Long userId = BaseContext.getCurrentId();
            haiGuiRankingService.recordUserAction(soupId, userId, "like");
            log.info("记录用户点赞行为: userId={}, soupId={}", userId, soupId);

        } catch (Exception e) {
            log.error("记录用户点赞行为失败: soupId={}", soupId, e);
        }
    }

    /**
     * 记录用户分享行为
     *
     * @param soupId 海龟汤ID
     */
    public void recordShareAction(String soupId) {
        try {
            Long userId = BaseContext.getCurrentId();
            haiGuiRankingService.recordUserAction(soupId, userId, "share");
            log.info("记录用户分享行为: userId={}, soupId={}", userId, soupId);

        } catch (Exception e) {
            log.error("记录用户分享行为失败: soupId={}", soupId, e);
        }
    }

    /**
     * 记录用户评论行为
     *
     * @param soupId 海龟汤ID
     */
    public void recordCommentAction(String soupId) {
        try {
            Long userId = BaseContext.getCurrentId();
            haiGuiRankingService.recordUserAction(soupId, userId, "comment");
            log.info("记录用户评论行为: userId={}, soupId={}", userId, soupId);

        } catch (Exception e) {
            log.error("记录用户评论行为失败: soupId={}", soupId, e);
        }
    }

    /**
     * 获取海龟汤的线索（兼容新旧两种格式）
     * 1. 新格式：从数据库查询线索，keyClues存储线索ID列表
     * 2. 旧格式：从JSON解析为GameClue列表，keyClues存储线索内容
     *
     * @param soupId 海龟汤ID
     * @return 线索列表
     */
    public List<GameClue> getSoupClues(String soupId) {
        try {
            HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElse(null);
            if (soup == null || soup.getKeyClues() == null) {
                log.warn("海龟汤不存在或keyClues为空: soupId={}", soupId);
                return List.of();
            }

            // 尝试解析为线索ID列表（新格式）
            List<String> clueIds = deserializeClueIds(soup.getKeyClues());
            if (!clueIds.isEmpty()) {
                // 新格式：使用线索片段表
                List<ClueFragment> fragments = clueFragmentRepository.findBySoupIdAndIsDeletedFalse(soupId);
                List<GameClue> gameClues = fragments.stream()
                        .map(fragment -> {
                            GameClue clue = new GameClue();
                            clue.setContent(fragment.getFragmentContent());
                            // 简化类型转换
                            try {
                                clue.setClueType(com.guanyu.haigui.Enum.ClueType.valueOf(fragment.getFragmentType()));
                            } catch (Exception e) {
                                clue.setClueType(com.guanyu.haigui.Enum.ClueType.PLOT);
                            }
                            clue.setIsKey(fragment.getIsCoreClue());
                            return clue;
                        })
                        .toList();

                log.info("获取海龟汤线索成功（新格式）: soupId={}, 线索数量={}", soupId, gameClues.size());
                return gameClues;
            }

            // 如果解析线索ID失败，尝试解析为线索内容（旧格式兼容）
            List<GameClue> clues = soupJsonParser.parseKeyClues(soup.getKeyClues());
            log.info("获取海龟汤线索成功（旧格式兼容）: soupId={}, 线索数量={}", soupId, clues.size());
            return clues;

        } catch (Exception e) {
            log.error("获取海龟汤线索失败: soupId={}", soupId, e);
            return List.of();
        }
    }

    /**
     * 获取海龟汤的关键线索（兼容新旧两种格式）
     *
     * @param soupId 海龟汤ID
     * @return 关键线索列表
     */
    public List<GameClue> getSoupKeyClues(String soupId) {
        try {
            HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElse(null);
            if (soup == null || soup.getKeyClues() == null) {
                log.warn("海龟汤不存在或keyClues为空: soupId={}", soupId);
                return List.of();
            }

            // 尝试解析为线索ID列表（新格式）
            List<String> clueIds = deserializeClueIds(soup.getKeyClues());
            if (!clueIds.isEmpty()) {
                // 新格式：使用线索片段表查询核心线索
                List<ClueFragment> fragments = clueFragmentRepository.findBySoupIdAndIsCoreClueTrueAndIsDeletedFalse(soupId);
                List<GameClue> gameClues = fragments.stream()
                        .map(fragment -> {
                            GameClue clue = new GameClue();
                            clue.setContent(fragment.getFragmentContent());
                            try {
                                clue.setClueType(com.guanyu.haigui.Enum.ClueType.valueOf(fragment.getFragmentType()));
                            } catch (Exception e) {
                                clue.setClueType(com.guanyu.haigui.Enum.ClueType.PLOT);
                            }
                            clue.setIsKey(true);
                            return clue;
                        })
                        .toList();

                log.info("获取海龟汤关键线索成功（新格式）: soupId={}, 关键线索数量={}", soupId, gameClues.size());
                return gameClues;
            }

            // 如果解析线索ID失败，使用旧格式兼容
            List<GameClue> allClues = getSoupClues(soupId);
            List<GameClue> keyClues = allClues.stream()
                    .filter(GameClue::isKeyClue)
                    .toList();
            log.info("获取海龟汤关键线索成功（旧格式兼容）: soupId={}, 关键线索数量={}", soupId, keyClues.size());
            return keyClues;

        } catch (Exception e) {
            log.error("获取海龟汤关键线索失败: soupId={}", soupId, e);
            return List.of();
        }
    }

    /**
     * 根据线索类型获取海龟汤线索
     *
     * @param soupId 海龟汤ID
     * @param clueType 线索类型
     * @return 指定类型的线索列表
     */
    public List<GameClue> getSoupCluesByType(String soupId, com.guanyu.haigui.Enum.ClueType clueType) {
        try {
            List<GameClue> allClues = getSoupClues(soupId);
            List<GameClue> filteredClues = allClues.stream()
                    .filter(clue -> clueType.equals(clue.getClueType()))
                    .toList();
            log.info("获取海龟汤线索成功: soupId={}, 类型={}, 线索数量={}", soupId, clueType, filteredClues.size());
            return filteredClues;
        } catch (Exception e) {
            log.error("获取海龟汤线索失败: soupId={}, 类型={}", soupId, clueType, e);
            return List.of();
        }
    }

    /**
     * 获取海龟汤热度排名
     *
     * @param soupId 海龟汤ID
     * @return 排名信息
     */
    public SoupRankInfo getSoupRankInfo(String soupId) {
        return haiGuiRankingService.getSoupRankInfo(soupId);
    }

    /**
     * 保存用户输入线索片段并进行向量化
     * @param soupId 海龟汤ID
     * @param clues 游戏线索列表
     */
    @Transactional
    public void saveUserCluesWithVectorization(String soupId, List<GameClue> clues) {
        for (int i = 0; i < clues.size(); i++) {
            try {
                GameClue gameClue = clues.get(i);

                // 创建线索片段
                ClueFragment fragment = new ClueFragment();
                fragment.setSoupId(soupId);
                fragment.setFragmentContent(gameClue.getContent());
                fragment.setFragmentType(gameClue.getClueType().toString());
                fragment.setInferenceLevel(1); // 用户线索默认为表层信息
                fragment.setIsCoreClue(gameClue.getIsKey());
                fragment.setFragmentOrder(i);
                fragment.setTriggerKeywords(java.util.Arrays.asList(gameClue.getContent().split(" ")));
                fragment.setGenerationSource("USER"); // 标记为用户输入
                fragment.setAiAnalysisConfidence(1.0); // 用户输入置信度为100%

                // 向量化处理
                try {
                    // 生成向量化文本（内容+关键词）
                    String keywords = fragment.getTriggerKeywords() != null ? String.join(" ", fragment.getTriggerKeywords()) : "";
                    String vectorText = fragment.getFragmentContent() + " " + keywords;

                    // 调用BGE向量服务
                    com.guanyu.haigui.pojo.vo.SingleEncodeResponse response = vectorClient.encodeSingle(vectorText);
                    List<Double> vector = response.getEmbeddings().get(0)
                            .stream()
                            .map(Float::doubleValue)
                            .collect(java.util.stream.Collectors.toList());

                    // 生成向量哈希
                    String vectorHash = generateVectorHash(vector);

                    fragment.setVectorData(vector);
                    fragment.setVectorHash(vectorHash);

                    // 将向量存储到Redis中，用于快速搜索
                    try {
                        // 新键结构：包含海龟汤ID和片段ID，便于指定汤内搜索
                        String redisKey = String.format("hai_gui:soup:%s:fragment:%s", soupId, fragment.getFragmentId());
                        List<Float> floatVector = vector.stream()
                                .map(Double::floatValue)
                                .collect(java.util.stream.Collectors.toList());
                        redisClient.storeVector(redisKey, floatVector);


                        // 不再使用集合键，直接使用具体片段键

                        // 将海龟汤ID添加到所有汤的集合中
                        redisClient.getCommands().sadd("hai_gui:soups:all", soupId);

                        // 在片段键中存储元数据（可选，用于调试）
                        String metadataKey = String.format("hai_gui:fragment:%s:meta", fragment.getFragmentId());
                        String metadata = String.format("{\"soupId\":\"%s\",\"content\":\"%s\",\"type\":\"%s\",\"isCore\":%s}",
                                soupId,
                                fragment.getFragmentContent().length() > 50 ? fragment.getFragmentContent().substring(0, 50) + "..." : fragment.getFragmentContent(),
                                fragment.getFragmentType(),
                                fragment.getIsCoreClue());
                        redisClient.getCommands().set(metadataKey, metadata);

                        log.debug("向量存储到Redis成功: soupId={}, fragmentId={}, dimension={}",
                                soupId, fragment.getFragmentId(), floatVector.size());
                    } catch (Exception redisEx) {
                        log.warn("向量存储到Redis失败: fragmentId={}, error={}",
                                fragment.getFragmentId(), redisEx.getMessage());
                        // Redis存储失败不影响MySQL存储
                    }

                    log.debug("用户线索片段向量化完成: {}", fragment.getFragmentContent());

                } catch (Exception e) {
                    log.error("用户线索片段向量化失败: {}", fragment.getFragmentContent(), e);
                    // 向量化失败时使用空向量，但不中断流程
                    fragment.setVectorData(new ArrayList<>());
                    fragment.setVectorHash("");
                }

                // 保存到数据库
                clueFragmentRepository.save(fragment);

                log.info("保存用户线索片段成功: content={}, type={}, isKey={}, vectorSize={}, fragmentId={}",
                        gameClue.getContent(), gameClue.getClueType(), gameClue.getIsKey(),
                        fragment.getVectorData() != null ? fragment.getVectorData().size() : 0, fragment.getFragmentId());
            } catch (Exception e) {
                log.error("保存用户线索片段失败: content={}", clues.get(i).getContent(), e);
            }
        }
    }

    /**
     * 保存线索片段到数据库（混合方案）- 保留兼容性
     * @param soupId 海龟汤ID
     * @param clues 游戏线索列表
     */
    @Transactional
    public void saveClueFragmentsToDatabase(String soupId, List<GameClue> clues) {
        saveUserCluesWithVectorization(soupId, clues);
    }

    // 注意：saveCluesToDatabase方法已被删除，现在所有线索保存都通过saveUserCluesWithVectorization处理

    /**
     * 构建用户线索分析的系统提示词
     */
    private String buildUserCluesAnalysisSystemPrompt(HaiGuiSoup soup) {
        return String.format("""
            你是一个海龟汤线索分析师。请分析用户上传的每条线索的属性。

            === 海龟汤信息 ===
            标题：%s
            汤面：%s
            汤底：%s

            === 分析要求 ===
            请对每条用户线索进行分析，包括：
            1. 线索类型（TIME/PLACE/CHARACTER/PLOT/OBJECT/TRUTH）
            2. 难度等级（1-5级，1最简单，5最核心）
            3. 是否关键线索（true/false）
            4. 关联的推理任务层级（1-3）
            5. 重要性评分（1-10）
            6. 分析理由

            === 输出格式要求 ===
            请严格按照以下格式返回，每行一个线索的分析结果：
            [线索内容]|[类型]|[难度]|[是否关键]|[任务层级]|[重要性]

            示例：凶手是男性|CHARACTER|3|true|2|8
            """, soup.getSoupTitle(), soup.getSoupSurface(), soup.getSoupBottom());
    }

    /**
     * 构建用户线索分析的用户提示词
     */
    private String buildUserCluesAnalysisPrompt(List<String> userClueContents) {
        StringBuilder prompt = new StringBuilder("请分析以下用户提供的线索：\n\n");

        for (int i = 0; i < userClueContents.size(); i++) {
            prompt.append(String.format("%d. %s\n", i + 1, userClueContents.get(i)));
        }

        prompt.append("\n请按照前面要求的格式返回每条线索的分析结果。");
        return prompt.toString();
    }

    /**
     * 保存AI分析后的用户线索
     */
    private void saveAiAnalyzedUserClues(String soupId, List<GameClue> clues, List<String> userClueContents, String[] aiResults) {
        for (int i = 0; i < clues.size(); i++) {
            GameClue gameClue = clues.get(i);
            try {
                String aiResult = i < aiResults.length ? aiResults[i] : "";

                // 解析AI分析结果
                String[] parts = aiResult.split("\\|");
                String fragmentType = parts.length > 1 ? parts[1] : "PLOT";
                Integer difficulty = parts.length > 2 ? tryParseInt(parts[2]) : 2;
                Boolean isCore = parts.length > 3 ? tryParseBoolean(parts[3]) : false;
                Integer associatedTask = parts.length > 4 ? tryParseInt(parts[4]) : 1;
                Integer importance = parts.length > 5 ? tryParseInt(parts[5]) : 5;

                // 创建线索片段
                ClueFragment fragment = new ClueFragment();
                fragment.setSoupId(soupId);
                fragment.setFragmentContent(gameClue.getContent());
                fragment.setFragmentType(fragmentType);
                fragment.setInferenceLevel(associatedTask);
                fragment.setIsCoreClue(isCore);
                fragment.setDifficulty(difficulty);
                fragment.setImportance(importance);
                fragment.setFragmentOrder(i);
                fragment.setTriggerKeywords(java.util.Arrays.asList(gameClue.getContent().split(" ")));
                fragment.setGenerationSource("USER_AI_ANALYZED"); // 标记为用户输入+AI分析
                fragment.setAiAnalysisConfidence(0.9); // AI分析的置信度
                fragment.setAssociatedTaskIds(List.of(associatedTask));

                // 向量化处理
                try {
                    String keywords = fragment.getTriggerKeywords() != null ? String.join(" ", fragment.getTriggerKeywords()) : "";
                    String vectorText = fragment.getFragmentContent() + " " + keywords;

                    SingleEncodeResponse response = BgeVectorClientUtil.encodeSingle(vectorText);
                    List<Double> vector = response.getEmbeddings().get(0)
                            .stream()
                            .map(Float::doubleValue)
                            .collect(java.util.stream.Collectors.toList());

                    String vectorHash = generateVectorHash(vector);
                    fragment.setVectorData(vector);
                    fragment.setVectorHash(vectorHash);

                    // 存储到Redis
                    String redisKey = String.format("hai_gui:soup:%s:fragment:%s", soupId, fragment.getFragmentId());
                    List<Float> floatVector = vector.stream()
                            .map(Double::floatValue)
                            .collect(java.util.stream.Collectors.toList());
                    redisClient.storeVector(redisKey, floatVector);

                    log.debug("用户线索向量存储完成: fragmentId={}", fragment.getFragmentId());

                } catch (Exception e) {
                    log.error("用户线索向量化失败: {}", gameClue.getContent(), e);
                    fragment.setVectorData(new ArrayList<>());
                    fragment.setVectorHash("");
                }

                // 保存到数据库
                ClueFragment savedFragment = clueFragmentRepository.save(fragment);

                log.info("保存AI分析用户线索: content={}, type={}, difficulty={}, importance={}, isCore={}, associatedTask={}, fragmentId={}",
                        gameClue.getContent(), fragmentType, difficulty, importance, isCore, associatedTask, savedFragment.getFragmentId());

            } catch (Exception e) {
                log.error("保存AI分析用户线索失败: content={}", gameClue.getContent(), e);
            }
        }
    }

    /**
     * 保存用户线索（使用默认属性）
     */
    private void saveUserCluesWithDefaultAttributes(String soupId, List<GameClue> clues) {
        for (int i = 0; i < clues.size(); i++) {
            GameClue gameClue = clues.get(i);
            try {
                // 创建线索片段（使用默认属性）
                ClueFragment fragment = new ClueFragment();
                fragment.setSoupId(soupId);
                fragment.setFragmentContent(gameClue.getContent());
                fragment.setFragmentType(gameClue.getClueType().toString());
                fragment.setInferenceLevel(1); // 默认为表层信息
                fragment.setIsCoreClue(gameClue.getIsKey());
                // 不设置difficulty和importance字段（数据库表中不存在）
                fragment.setFragmentOrder(i);
                fragment.setTriggerKeywords(java.util.Arrays.asList(gameClue.getContent().split(" ")));
                fragment.setGenerationSource("USER_DEFAULT");
                fragment.setAiAnalysisConfidence(0.3); // 低置信度
                fragment.setAssociatedTaskIds(List.of(1));

                // 保存到数据库
                ClueFragment savedFragment = clueFragmentRepository.save(fragment);

                log.info("保存默认用户线索: fragmentId={}", savedFragment.getFragmentId());

            } catch (Exception e) {
                log.error("保存默认用户线索失败: content={}", gameClue.getContent(), e);
            }
        }
    }

    /**
     * 尝试解析整数
     */
    private Integer tryParseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            return 2; // 默认中等难度
        }
    }

    /**
     * 尝试解析布尔值
     */
    private Boolean tryParseBoolean(String str) {
        try {
            return Boolean.parseBoolean(str);
        } catch (Exception e) {
            return false; // 默认非关键线索
        }
    }

    /**
     * 将线索ID列表序列化为JSON数组
     * @param clueIds 线索ID列表
     * @return JSON数组字符串
     */
    private String serializeClueIds(List<String> clueIds) {
        try {
            return new ObjectMapper().writeValueAsString(clueIds);
        } catch (Exception e) {
            log.error("序列化线索ID列表失败", e);
            return "[]"; // 返回空数组作为fallback
        }
    }

    /**
     * 将线索ID列表序列化为JSON数组（支持Long类型）
     * @param clueIds 线索ID列表
     * @return JSON数组字符串
     */
    private String serializeLongClueIds(List<Long> clueIds) {
        try {
            List<String> stringIds = clueIds.stream()
                    .map(id -> id.toString())
                    .collect(java.util.stream.Collectors.toList());
            return new ObjectMapper().writeValueAsString(stringIds);
        } catch (Exception e) {
            log.error("序列化线索ID列表失败", e);
            return "[]"; // 返回空数组作为fallback
        }
    }

    /**
     * 从JSON数组反序列化线索ID列表
     * @param clueIdsJson JSON数组字符串
     * @return 线索ID列表
     */
    public List<String> deserializeClueIds(String clueIdsJson) {
        try {
            if (clueIdsJson == null || clueIdsJson.trim().isEmpty()) {
                return new ArrayList<>();
            }
            return new ObjectMapper().readValue(clueIdsJson, List.class);
        } catch (Exception e) {
            log.error("反序列化线索ID列表失败: {}", clueIdsJson, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取海龟汤的所有线索片段（从数据库查询）
     * @param soupId 海龟汤ID
     * @return 线索片段列表
     */
    public List<ClueFragment> getSoupClueFragmentsFromDatabase(String soupId) {
        try {
            return clueFragmentRepository.findBySoupIdAndIsDeletedFalse(soupId);
        } catch (Exception e) {
            log.error("从数据库获取海龟汤线索片段失败: soupId={}", soupId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取海龟汤的关键线索片段（从数据库查询）
     * @param soupId 海龟汤ID
     * @return 关键线索片段列表
     */
    public List<ClueFragment> getSoupKeyClueFragmentsFromDatabase(String soupId) {
        try {
            return clueFragmentRepository.findBySoupIdAndIsCoreClueTrueAndIsDeletedFalse(soupId);
        } catch (Exception e) {
            log.error("从数据库获取海龟汤关键线索片段失败: soupId={}", soupId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 更新海龟汤的线索信息（包括线索ID列表和拆解配置）
     * @param soup 海龟汤对象
     * @param fragments AI拆解的线索片段列表
     */
    private void updateSoupWithClueInfo(HaiGuiSoup soup, List<ClueFragment> fragments) {
        try {
            // 1. 更新关键线索ID列表
            List<Long> keyClueIds = fragments.stream()
                    .filter(ClueFragment::getIsCoreClue)
                    .map(ClueFragment::getFragmentId)
                    .collect(java.util.stream.Collectors.toList());

            // 2. 序列化为JSON数组
            String keyCluesJson = serializeLongClueIds(keyClueIds);
            soup.setKeyClues(keyCluesJson);

            // 3. 创建线索拆解配置
            Map<String, Object> decompositionConfig = new HashMap<>();
            decompositionConfig.put("total_fragments", fragments.size());
            decompositionConfig.put("key_fragments", keyClueIds.size());
            decompositionConfig.put("fragment_types",
                fragments.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                        ClueFragment::getFragmentType,
                        java.util.stream.Collectors.counting()
                    ))
            );
            decompositionConfig.put("inference_levels",
                fragments.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                        ClueFragment::getInferenceLevel,
                        java.util.stream.Collectors.counting()
                    ))
            );
            decompositionConfig.put("decomposition_method", debugMode ? "AI_DEBUG" : "AI_REAL");
            decompositionConfig.put("decomposition_time", java.time.LocalDateTime.now());

            // 4. 序列化配置为JSON
            String configJson = objectMapper.writeValueAsString(decompositionConfig);

            // 5. 如果HaiGuiSoup模型有clue_decomposition_config字段，则保存
            // 这里我们使用现有的字段，如果需要新增字段，需要修改HaiGuiSoup模型
            log.info("线索拆解配置: 总片段数={}, 关键线索数={}",
                    fragments.size(), keyClueIds.size());

        } catch (Exception e) {
            log.error("更新海龟汤线索信息失败", e);
        }
    }

    /**
     * 生成向量数据的哈希值
     */
    private String generateVectorHash(List<Double> vector) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String vectorString = vector.stream()
                    .map(d -> String.format("%.6f", d))
                    .collect(Collectors.joining(","));
            byte[] hash = md.digest(vectorString.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("生成向量哈希失败", e);
            return "";
        }
    }

    /**
     * 生成推理任务并关联线索（混合方案）
     * @param soupId 海龟汤ID
     * @param aiFragments AI拆解的线索片段
     */
    @Transactional
    public void generateInferenceTasksWithClueAssociation(String soupId, List<ClueFragment> aiFragments) {
        try {
            // 生成基础推理任务
            InferenceTask task1 = new InferenceTask(soupId, "发现基本信息", "询问故事的基本背景和设定", 1);
            task1.setReasoningGoal("掌握故事的基本时间、地点、人物等背景信息");
            task1.setProgressWeight(20.0);
            task1.setTaskOrder(1);

            InferenceTask task2 = new InferenceTask(soupId, "理解内在联系", "理解各要素之间的关系", 2);
            task2.setReasoningGoal("理解事件之间的因果关系和人物动机");
            task2.setProgressWeight(30.0);
            task2.setTaskOrder(2);

            InferenceTask task3 = new InferenceTask(soupId, "推理深层真相", "发现隐藏的关键信息", 3);
            task3.setReasoningGoal("揭示故事的完整真相和核心秘密");
            task3.setProgressWeight(50.0);
            task3.setTaskOrder(3);

            // 保存推理任务并获取真实ID
            InferenceTask savedTask1 = inferenceTaskRepository.save(task1);
            InferenceTask savedTask2 = inferenceTaskRepository.save(task2);
            InferenceTask savedTask3 = inferenceTaskRepository.save(task3);

            // 创建任务序号到真实ID的映射
            Map<Integer, Long> taskOrderToIdMap = Map.of(
                1, savedTask1.getTaskId(),
                2, savedTask2.getTaskId(),
                3, savedTask3.getTaskId()
            );

            // 更新线索片段的任务关联（使用真实任务ID）
            for (ClueFragment fragment : aiFragments) {
                if (fragment.getAssociatedTaskIds() != null && !fragment.getAssociatedTaskIds().isEmpty()) {
                    List<Integer> taskOrders = fragment.getAssociatedTaskIds();
                    List<Integer> realTaskIds = taskOrders.stream()
                            .filter(taskOrderToIdMap::containsKey)
                            .map(taskOrderToIdMap::get)
                            .map(Long::intValue)  // 转换Long到Integer
                            .collect(java.util.stream.Collectors.toList());

                    fragment.setAssociatedTaskIds(realTaskIds);
                    clueFragmentRepository.save(fragment);
                }
            }

            log.info("生成推理任务成功: soupId={}, 任务数量=3", soupId);

        } catch (Exception e) {
            log.error("生成推理任务失败: soupId={}", soupId, e);
        }
    }

    /**
     * 生成推理任务（混合方案）
     * @param soupId 海龟汤ID
     */
    @Transactional
    public void generateInferenceTasksForSoup(String soupId) {
        try {
            // 生成基础推理任务
            InferenceTask task1 = new InferenceTask(soupId, "发现基本信息", "询问故事的基本背景和设定", 1);
            task1.setReasoningGoal("掌握故事的基本时间、地点、人物等背景信息");
            task1.setProgressWeight(20.0);
            task1.setTaskOrder(1);

            InferenceTask task2 = new InferenceTask(soupId, "理解内在联系", "理解各要素之间的关系", 2);
            task2.setReasoningGoal("理解事件之间的因果关系和人物动机");
            task2.setProgressWeight(30.0);
            task2.setTaskOrder(2);

            InferenceTask task3 = new InferenceTask(soupId, "推理深层真相", "发现隐藏的关键信息", 3);
            task3.setReasoningGoal("揭示故事的完整真相和核心秘密");
            task3.setProgressWeight(50.0);
            task3.setTaskOrder(3);

            // 保存推理任务
            inferenceTaskRepository.save(task1);
            inferenceTaskRepository.save(task2);
            inferenceTaskRepository.save(task3);

            log.info("生成推理任务成功: soupId={}, 任务数量=3", soupId);

        } catch (Exception e) {
            log.error("生成推理任务失败: soupId={}", soupId, e);
        }
    }
}