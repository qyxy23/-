package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.CreateHaiGuiSoupDTO;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.result.DecompositionResult;
import com.guanyu.haigui.pojo.vo.ClueMatchResult;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final RedisStackClient redisClient;
    private final BgeVectorClientUtil vectorClient;
    private final UserInfoRepository userInfoRepository;
    private final SoupJsonParser soupJsonParser;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final ClueFragmentRepository clueFragmentRepository;
    private final InferenceTaskRepository inferenceTaskRepository;
    private final ClueDecompositionService clueDecompositionService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final boolean debugMode = false;


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

            log.info("原始progressSettings输入类型: {}, 值: '{}'",
                    soup.getProgressSettings() != null ? soup.getProgressSettings().getClass().getSimpleName() : "null",
                    progressSettingsInput);

            // 解析线索列表（使用GameClue业务实体）
            List<GameClue> clues = soupJsonParser.parseKeyClues(keyCluesInput);
            log.info("解析得到的线索数量: {}", clues.size());

            // 解析进度设置中的任务列表（在非调试模式下使用）
            List<InferenceTask> userProvidedTasks = parseUserProvidedTasks(progressSettingsInput);
            log.info("解析得到的用户任务数量: {}", userProvidedTasks.size());

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
            List<Map<String, Object>> aiInferenceTasks;
            try {
                log.info("开始为海龟汤生成AI拆解的线索片段和推理任务，同时分析用户线索，用户线索数量: {}", clues.size());

                // 使用ClueDecompositionService拆解汤底并分析用户线索，同时传递用户提供的任务
                DecompositionResult decompositionResult = clueDecompositionService.decomposeSoupBottomWithUserCluesAndTasks(
                        savedSoup.getSoupTitle(),
                        savedSoup.getSoupSurface(),
                        savedSoup.getSoupBottom(),
                        clues,
                        userProvidedTasks
                    );

                aiFragments = decompositionResult.getFragments();
                aiInferenceTasks = decompositionResult.getInferenceTasks();

                log.info("AI拆解完成，生成{}个片段和{}个推理任务", aiFragments.size(), aiInferenceTasks.size());

                // 3. 优先生成推理任务（关键修复：先生成任务）
                Map<Integer, Long> taskOrderToIdMap = new HashMap<>();
                if (aiInferenceTasks != null && !aiInferenceTasks.isEmpty()) {
                    log.info("开始保存AI生成的推理任务，任务数量: {}", aiInferenceTasks.size());

                    for (Map<String, Object> taskData : aiInferenceTasks) {
                        try {
                            // 解析AI任务数据
                            String taskName = (String) taskData.get("taskName");
                            String description = (String) taskData.get("description");
                            Integer understandingLevel = (Integer) taskData.getOrDefault("understandingLevel", 1);
                            String reasoningGoal = (String) taskData.get("reasoningGoal");
                            Double progressWeight = ((Number) taskData.getOrDefault("progressWeight", 30.0)).doubleValue();
                            Boolean isMandatory = (Boolean) taskData.getOrDefault("isMandatory", false);
                            Integer taskOrder = (Integer) taskData.getOrDefault("taskOrder", 1);

                            // 创建推理任务对象
                            InferenceTask task = new InferenceTask(savedSoup.getSoupId(), taskName, description, understandingLevel);
                            task.setReasoningGoal(reasoningGoal);
                            task.setProgressWeight(progressWeight);
                            task.setIsMandatory(isMandatory);
                            task.setTaskOrder(taskOrder);

                            // 保存任务并获取真实ID
                            InferenceTask savedTask = inferenceTaskRepository.save(task);
                            taskOrderToIdMap.put(taskOrder, savedTask.getTaskId());

                            log.info("保存AI推理任务成功: taskOrder={}, taskName={}, taskId={}",
                                taskOrder, taskName, savedTask.getTaskId());

                        } catch (Exception e) {
                            log.error("保存AI推理任务失败", e);
                        }
                    }
                }

                // 如果AI任务保存失败，生成默认任务
                if (taskOrderToIdMap.isEmpty()) {
                    log.warn("AI推理任务全部保存失败，生成默认推理任务");
                    taskOrderToIdMap = generateDefaultInferenceTasks(savedSoup.getSoupId());
                }

                // 4. 保存AI生成的线索片段到数据库（现在有了任务ID）
                for (ClueFragment fragment : aiFragments) {
                    fragment.setSoupId(savedSoup.getSoupId());
                    // 确保fragmentId为null，让JPA自动生成
                    fragment.setFragmentId(null);

                    // 关键修复：将任务序号转换为真实的任务ID
                    if (fragment.getAssociatedTaskIds() != null && !fragment.getAssociatedTaskIds().isEmpty()) {
                        List<Integer> taskOrders = fragment.getAssociatedTaskIds();
                        List<Integer> realTaskIds = taskOrders.stream()
                                .filter(taskOrderToIdMap::containsKey)
                                .map(taskOrderToIdMap::get)
                                .map(Long::intValue)  // 转换Long到Integer
                                .collect(java.util.stream.Collectors.toList());

                        fragment.setAssociatedTaskIds(realTaskIds);
                        log.debug("更新线索片段任务关联: fragmentContent={}, taskOrders={}, realTaskIds={}",
                            fragment.getFragmentContent(), taskOrders, realTaskIds);
                    }

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

                            log.info("AI线索片段向量存储到Redis成功: soupId={}, fragmentId={}, dimension={}, taskIds={}",
                                    savedSoup.getSoupId(), savedFragment.getFragmentId(), floatVector.size(), savedFragment.getAssociatedTaskIds());
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

                log.info("AI拆解线索片段完成，生成{}个片段和{}个推理任务", aiFragments.size(), taskOrderToIdMap.size());

            } catch (Exception e) {
                log.warn("AI拆解线索片段失败: {}", e.getMessage());
                // AI拆解失败时，仍然生成推理任务并关联线索
                // 即使没有AI生成的任务，也要确保线索片段有关联的任务ID
                generateInferenceTasksWithClueAssociation(savedSoup.getSoupId(), aiFragments);
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

                        // 计算匹配原因
                        String matchReason = generateMatchReason(similarity);
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
    private String generateMatchReason(double similarity) {
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
            List clueIds = deserializeClueIds(soup.getKeyClues());
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
     *
     * @param clueIdsJson JSON数组字符串
     * @return 线索ID列表
     */
    public List deserializeClueIds(String clueIdsJson) {
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
     * 使用AI生成的推理任务并关联线索（新方案）
     * @param soupId 海龟汤ID
     * @param aiInferenceTasks AI生成的推理任务（taskOrder为1,2,3等）
     * @param aiFragments AI拆解的线索片段
     */
    @Transactional
    public void generateInferenceTasksWithAITasks(String soupId, List<Map<String, Object>> aiInferenceTasks, List<ClueFragment> aiFragments) {
        try {
            if (aiInferenceTasks == null || aiInferenceTasks.isEmpty()) {
                log.warn("AI未生成推理任务，使用默认方案");
                generateInferenceTasksWithClueAssociation(soupId, aiFragments);
                return;
            }

            log.info("开始使用AI生成的推理任务，任务数量: {}", aiInferenceTasks.size());

            // 保存AI生成的推理任务并获取真实ID
            Map<Integer, Long> taskOrderToIdMap = new HashMap<>();

            for (Map<String, Object> taskData : aiInferenceTasks) {
                try {
                    // 解析AI任务数据
                    String taskName = (String) taskData.get("taskName");
                    String description = (String) taskData.get("description");
                    Integer understandingLevel = (Integer) taskData.getOrDefault("understandingLevel", 1);
                    String reasoningGoal = (String) taskData.get("reasoningGoal");
                    Double progressWeight = ((Number) taskData.getOrDefault("progressWeight", 30.0)).doubleValue();
                    Boolean isMandatory = (Boolean) taskData.getOrDefault("isMandatory", false);
                    Integer taskOrder = (Integer) taskData.getOrDefault("taskOrder", 1);

                    // 创建推理任务对象
                    InferenceTask task = new InferenceTask(soupId, taskName, description, understandingLevel);
                    task.setReasoningGoal(reasoningGoal);
                    task.setProgressWeight(progressWeight);
                    task.setIsMandatory(isMandatory);
                    task.setTaskOrder(taskOrder);

                    // 保存任务并获取真实ID
                    InferenceTask savedTask = inferenceTaskRepository.save(task);
                    taskOrderToIdMap.put(taskOrder, savedTask.getTaskId());

                    log.info("保存AI推理任务成功: taskOrder={}, taskName={}, taskId={}",
                        taskOrder, taskName, savedTask.getTaskId());

                } catch (Exception e) {
                    log.error("保存AI推理任务失败", e);
                }
            }

            // 如果AI任务保存失败，回退到默认方案
            if (taskOrderToIdMap.isEmpty()) {
                log.warn("AI推理任务全部保存失败，使用默认方案");
                generateInferenceTasksWithClueAssociation(soupId, aiFragments);
                return;
            }

            // 更新线索片段的任务关联（将AI返回的taskOrder 1,2,3替换为真实ID）
            int updatedFragments = 0;
            for (ClueFragment fragment : aiFragments) {
                try {
                    if (fragment.getAssociatedTaskIds() != null && !fragment.getAssociatedTaskIds().isEmpty()) {
                        List<Integer> taskOrders = fragment.getAssociatedTaskIds();
                        List<Integer> realTaskIds = taskOrders.stream()
                                .filter(taskOrderToIdMap::containsKey)
                                .map(taskOrderToIdMap::get)
                                .map(Long::intValue)  // 转换Long到Integer
                                .collect(java.util.stream.Collectors.toList());

                        // 只有成功关联到真实任务的线索才更新
                        if (!realTaskIds.isEmpty()) {
                            fragment.setAssociatedTaskIds(realTaskIds);
                            clueFragmentRepository.save(fragment);
                            updatedFragments++;

                            log.debug("更新线索片段任务关联: fragmentId={}, taskOrders={}, realTaskIds={}",
                                fragment.getFragmentId(), taskOrders, realTaskIds);
                        }
                    }
                } catch (Exception e) {
                    log.error("更新线索片段任务关联失败: fragmentId={}", fragment.getFragmentId(), e);
                }
            }

            log.info("AI推理任务生成完成: soupId={}, 任务数量={}, 关联线索数量={}",
                soupId, taskOrderToIdMap.size(), updatedFragments);

        } catch (Exception e) {
            log.error("使用AI推理任务失败，回退到默认方案: soupId={}", soupId, e);
            generateInferenceTasksWithClueAssociation(soupId, aiFragments);
        }
    }

    /**
     * 生成默认推理任务（当AI失败时）
     * @param soupId 海龟汤ID
     * @return 任务序号到真实ID的映射
     */
    private Map<Integer, Long> generateDefaultInferenceTasks(String soupId) {
        Map<Integer, Long> taskOrderToIdMap = new HashMap<>();
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
            taskOrderToIdMap.put(1, savedTask1.getTaskId());
            taskOrderToIdMap.put(2, savedTask2.getTaskId());
            taskOrderToIdMap.put(3, savedTask3.getTaskId());

            log.info("生成默认推理任务成功: soupId={}, 任务数量=3", soupId);

        } catch (Exception e) {
            log.error("生成默认推理任务失败: soupId={}", soupId, e);
        }
        return taskOrderToIdMap;
    }

    /**
     * 解析用户提供的推理任务列表
     * @param progressSettingsInput 进度设置字符串（JSON格式）
     * @return 用户提供的推理任务列表
     */
    private List<InferenceTask> parseUserProvidedTasks(String progressSettingsInput) {
        if (progressSettingsInput == null || progressSettingsInput.trim().isEmpty()) {
            log.debug("用户未提供推理任务，使用默认任务");
            return new ArrayList<>();
        }

        try {
            // 尝试解析为JSON
            Object progressSettingsObj = objectMapper.readValue(progressSettingsInput, Object.class);

            // 如果是数组，直接解析为任务列表
            if (progressSettingsObj instanceof List) {
                List<Map<String, Object>> tasksData = (List<Map<String, Object>>) progressSettingsObj;
                List<InferenceTask> tasks = new ArrayList<>();

                for (Map<String, Object> taskData : tasksData) {
                    try {
                        String taskName = (String) taskData.getOrDefault("taskName", "未知任务");
                        String description = (String) taskData.getOrDefault("description", "");
                        Integer understandingLevel = (Integer) taskData.getOrDefault("understandingLevel", 1);
                        String reasoningGoal = (String) taskData.getOrDefault("reasoningGoal", "");
                        Double progressWeight = ((Number) taskData.getOrDefault("progressWeight", 30.0)).doubleValue();
                        Boolean isMandatory = (Boolean) taskData.getOrDefault("isMandatory", true);
                        Integer taskOrder = (Integer) taskData.getOrDefault("taskOrder", tasks.size() + 1);
                        List<String> targetKeywords = (List<String>) taskData.getOrDefault("targetKeywords", new ArrayList<>());

                        InferenceTask task = new InferenceTask();
                        task.setTaskName(taskName);
                        task.setTaskDescription(description);
                        task.setUnderstandingLevel(understandingLevel);
                        task.setReasoningGoal(reasoningGoal);
                        task.setProgressWeight(progressWeight);
                        task.setIsMandatory(isMandatory);
                        task.setTaskOrder(taskOrder);
                        task.setTargetKeywords(targetKeywords);
                        task.setIsDeleted(false);

                        tasks.add(task);

                        log.debug("解析用户任务: taskName={}, taskOrder={}, isMandatory={}",
                            taskName, taskOrder, isMandatory);

                    } catch (Exception e) {
                        log.warn("解析单个用户任务失败", e);
                    }
                }

                log.info("成功解析{}个用户提供的推理任务", tasks.size());
                return tasks;

            } else {
                // 如果是其他格式，暂时忽略
                log.debug("progressSettings不是任务列表格式，忽略: {}", progressSettingsInput);
                return new ArrayList<>();
            }

        } catch (Exception e) {
            log.warn("解析用户提供的推理任务失败: {}", progressSettingsInput, e);
            throw new BusinessException(500, "解析用户提供的推理任务失败");
        }
    }

}