package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.guanyu.haigui.Exception.AiResponseException;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.CreateHaiGuiSoupDTO;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.InferenceTask;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.result.DecompositionResult;
import com.guanyu.haigui.pojo.vo.ClueMatchResult;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.repository.ClueFragmentRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.InferenceTaskRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.VectorService;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.MinioUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import com.guanyu.haigui.utils.SoupJsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
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
    private final RedisStackClient redisClient;
    private final UserInfoRepository userInfoRepository;
    private final SoupJsonParser soupJsonParser;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final ClueFragmentRepository clueFragmentRepository;
    private final InferenceTaskRepository inferenceTaskRepository;
    private final ClueDecompositionService clueDecompositionService;
    private final MinioUtil minioUtil;


    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());



    /**
     * 新增海龟汤（包含向量化处理和智能线索解析）
     *
     * @param soup 海龟汤对象
     * @return 是否成功
     */
    @Transactional
    public boolean addTurtleSoup(CreateHaiGuiSoupDTO soup) {
        try {
            // 1. 创建并保存海龟汤实体
            HaiGuiSoup haiGuiSoup = createHaiGuiSoupEntity(soup);
            HaiGuiSoup savedSoup = haiGuiSoupRepository.save(haiGuiSoup);
            log.info("海龟汤保存到数据库成功: soupId={}", savedSoup.getSoupId());

            // 2. 使用AI拆解汤底
            List<ClueFragment> aiFragments;
            List<Map<String, Object>> aiInferenceTasks;
            try {
                log.info("开始为海龟汤生成AI拆解的线索片段和推理任务");

                DecompositionResult decompositionResult = clueDecompositionService
                        .decomposeWithAIAndUserCluesAndTasks(
                                savedSoup,
                                soupJsonParser.parseKeyClues(soup.getKeyCluesAsString()),
                                parseUserProvidedTasks(soup.getProgressSettingsAsString())
                        );

                aiFragments = decompositionResult.getFragments();
                aiInferenceTasks = decompositionResult.getInferenceTasks();
                log.info("AI拆解完成，生成{}个片段和{}个任务", aiFragments.size(), aiInferenceTasks.size());

                // 3. 先保存所有线索片段（确保ID生成）
                Map<Integer, Long> fragmentOrderToIdMap = saveClueFragments(savedSoup, aiFragments);
                log.info("保存了{}个线索片段", fragmentOrderToIdMap.size());

                // 4. 处理任务数据：将前置线索序号替换为真实ID
                List<Map<String, Object>> processedTasksData = processTaskDataForSaving(aiInferenceTasks, fragmentOrderToIdMap);

                // 5. 保存推理任务（使用处理后的数据）
                Map<Integer, Long> taskOrderToIdMap = new HashMap<>();
                if (!processedTasksData.isEmpty()) {
                    log.info("开始保存AI生成的推理任务，任务数量: {}", processedTasksData.size());

                    for (Map<String, Object> taskData : processedTasksData) {
                        try {
                            InferenceTask task = createInferenceTaskWithProcessedData(savedSoup, taskData);

                            // 保存任务
                            InferenceTask savedTask = inferenceTaskRepository.saveAndFlush(task);
                            taskOrderToIdMap.put(task.getTaskOrder(), savedTask.getTaskId());

                            log.info("保存AI推理任务成功: taskOrder={}, taskName={}, taskId={}",
                                    task.getTaskOrder(), task.getTaskName(), savedTask.getTaskId());

                        } catch (Exception e) {
                            log.error("保存AI推理任务失败", e);
                            createDefaultTask(savedSoup, taskOrderToIdMap);
                        }
                    }
                }

                // 如果AI任务保存失败，生成默认任务
                if (taskOrderToIdMap.isEmpty()) {
                    log.warn("AI推理任务全部保存失败，生成默认推理任务");
                    createDefaultTask(savedSoup, taskOrderToIdMap);
                }

                // 6. 更新海龟汤的关键线索ID列表
                updateSoupWithClueInfo(savedSoup, aiFragments);
                haiGuiSoupRepository.saveAndFlush(savedSoup);


                log.info("海龟汤新增成功: soupId={}", savedSoup.getSoupId());
                return true;

            } catch (Exception e) {
                log.warn("AI拆解线索片段失败: {}", e.getMessage());
                throw new AiResponseException("AI拆解失败");
            }

        } catch (Exception e) {
            log.error("新增海龟汤失败: title={}", soup.getSoupTitle(), e);
            throw new BusinessException(500, "新增海龟汤失败: " + e.getMessage());
        }
    }

    // 处理任务数据：将前置线索序号替换为真实ID
    private List<Map<String, Object>> processTaskDataForSaving(
            List<Map<String, Object>> originalTasksData,
            Map<Integer, Long> fragmentOrderToIdMap) {

        List<Map<String, Object>> processedTasksData = new ArrayList<>();

        for (Map<String, Object> taskData : originalTasksData) {
            // 创建任务数据的深拷贝
            Map<String, Object> processedTaskData = new HashMap<>(taskData);

            // 获取前置线索序号列表
            List<Integer> prereqOrders = new ArrayList<>();
            Object rawPrereq = taskData.get("prerequisiteFragmentIds");

            if (rawPrereq instanceof List<?>) {
                for (Object item : (List<?>) rawPrereq) {
                    if (item instanceof Integer) {
                        prereqOrders.add((Integer) item);
                    } else if (item instanceof Number) {
                        prereqOrders.add(((Number) item).intValue());
                    } else if (item != null) {
                        try {
                            prereqOrders.add(Integer.parseInt(item.toString()));
                        } catch (NumberFormatException e) {
                            log.warn("无法解析前置线索ID: {}", item);
                        }
                    }
                }
            }

            // 过滤无效值
            prereqOrders = prereqOrders.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            // 将序号转换为真实ID
            List<String> realPrereqIds = new ArrayList<>();
            for (Integer order : prereqOrders) {
                Long fragmentId = fragmentOrderToIdMap.get(order);
                if (fragmentId != null) {
                    realPrereqIds.add(String.valueOf(fragmentId));
                } else {
                    log.warn("未找到片段序号为{}的片段，跳过", order);
                }
            }

            // 更新处理后的任务数据
            processedTaskData.put("prerequisiteFragmentIds", realPrereqIds);
            processedTasksData.add(processedTaskData);
        }

        return processedTasksData;
    }

    // 使用处理后的数据创建任务
    private InferenceTask createInferenceTaskWithProcessedData(HaiGuiSoup soup, Map<String, Object> processedTaskData) {
        InferenceTask task = new InferenceTask();
        task.setSoupId(soup.getSoupId());
        task.setTaskName((String) processedTaskData.get("taskName"));
        task.setTaskDescription((String) processedTaskData.get("taskDescription"));

        // 处理可能为null的值
        Object understandingLevel = processedTaskData.get("understandingLevel");
        task.setUnderstandingLevel(understandingLevel instanceof Integer ? (Integer) understandingLevel : 1);

        task.setReasoningGoal((String) processedTaskData.get("reasoningGoal"));

        Object progressWeight = processedTaskData.get("progressWeight");
        task.setProgressWeight(progressWeight instanceof Number ? ((Number) progressWeight).doubleValue() : 30.0);

        Object isMandatory = processedTaskData.get("isMandatory");
        task.setIsMandatory(isMandatory instanceof Boolean ? (Boolean) isMandatory : true);

        Object taskOrder = processedTaskData.get("taskOrder");
        task.setTaskOrder(taskOrder instanceof Integer ? (Integer) taskOrder : 1);

        // 关键修复：使用处理后的前置线索ID（已经是真实ID）
        Object prerequisiteIds = processedTaskData.get("prerequisiteFragmentIds");
        if (prerequisiteIds instanceof List<?>) {
            List<String> ids = new ArrayList<>();
            for (Object idObj : (List<?>) prerequisiteIds) {
                if (idObj instanceof String) {
                    ids.add((String) idObj);
                } else if (idObj != null) {
                    ids.add(idObj.toString());
                }
            }
            task.setPrerequisiteFragmentIds(ids);
        } else {
            // 如果没有前置线索，设置为空列表
            task.setPrerequisiteFragmentIds(new ArrayList<>());
        }

        // 处理目标关键词
        Object targetKeywords = processedTaskData.get("targetKeywords");
        if (targetKeywords instanceof List<?>) {
            List<String> keywords = new ArrayList<>();
            for (Object kwObj : (List<?>) targetKeywords) {
                if (kwObj instanceof String) {
                    keywords.add((String) kwObj);
                } else if (kwObj != null) {
                    keywords.add(kwObj.toString());
                }
            }
            task.setTargetKeywords(keywords);
        } else {
            task.setTargetKeywords(new ArrayList<>());
        }

        return task;
    }

    // 保存线索片段（保持不变）
    private Map<Integer, Long> saveClueFragments(HaiGuiSoup soup, List<ClueFragment> fragments) {
        Map<Integer, Long> fragmentOrderToIdMap = new HashMap<>();

        for (ClueFragment fragment : fragments) {
            fragment.setSoupId(soup.getSoupId());
            fragment.setFragmentId(null); // 重置ID
            List<Float> FragmentVector = vectorizeFragment(fragment.getFragmentContent());
            fragment.setVectorData(FragmentVector);


            // 确保向量数据不为null
            if (fragment.getVectorData() == null) {
                fragment.setVectorData(new ArrayList<>());
            }

            // 保存并刷新
            ClueFragment savedFragment = clueFragmentRepository.saveAndFlush(fragment);
            String soupFragmentKey = String.format("hai_gui:soup:%s:fragment:%s", soup.getSoupId(), fragment.getFragmentId());
            redisClient.storeVector(soupFragmentKey, fragment.getVectorData());
            fragmentOrderToIdMap.put(savedFragment.getFragmentOrder(), savedFragment.getFragmentId());
        }

        return fragmentOrderToIdMap;
    }

    public List<Float> vectorizeFragment(String FragmentVector) {
        try {
            log.debug("开始向量化线索: {}", FragmentVector.substring(0, Math.min(30, FragmentVector.length())));

            // 使用BGE模型向量化
            SingleEncodeResponse response = BgeVectorClientUtil.encodeSingle(FragmentVector);
            if (response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
                log.error("BGE向量化失败: {}", FragmentVector);
                return Collections.emptyList();
            }

            List<Float> vector = response.getEmbeddings().get(0);
            log.debug("线索向量化成功，维度: {}", vector.size());

            return vector;

        } catch (Exception e) {
            log.error("向量化线索失败: {}", FragmentVector, e);
            return Collections.emptyList();
        }
    }

    // 创建默认任务（使用真实ID）
    private void createDefaultTask(HaiGuiSoup soup, Map<Integer, Long> taskOrderToIdMap) {
        try {
            InferenceTask defaultTask = new InferenceTask();
            defaultTask.setSoupId(soup.getSoupId());
            defaultTask.setTaskName("分析案件基本事实");
            defaultTask.setTaskDescription("理解案件的基本情况和关键要素");
            defaultTask.setUnderstandingLevel(1);
            defaultTask.setReasoningGoal("掌握案件的核心问题和背景");
            defaultTask.setProgressWeight(100.0);
            defaultTask.setIsMandatory(true);
            defaultTask.setTaskOrder(1);

            // 关键修复：设置为空列表
            defaultTask.setPrerequisiteFragmentIds(new ArrayList<>());
            defaultTask.setTargetKeywords(new ArrayList<>());

            InferenceTask savedTask = inferenceTaskRepository.saveAndFlush(defaultTask);
            taskOrderToIdMap.put(1, savedTask.getTaskId());

            log.warn("已创建默认推理任务: taskId={}", savedTask.getTaskId());
        } catch (Exception ex) {
            log.error("创建默认任务失败", ex);
            // 终极回退：创建最简单的任务
            createFallbackTask(soup, taskOrderToIdMap);
        }
    }

    // 终极回退：创建最简单的任务
    private void createFallbackTask(HaiGuiSoup soup, Map<Integer, Long> taskOrderToIdMap) {
        try {
            InferenceTask fallbackTask = new InferenceTask();
            fallbackTask.setSoupId(soup.getSoupId());
            fallbackTask.setTaskName("默认任务");
            fallbackTask.setTaskDescription("请分析案件");
            fallbackTask.setUnderstandingLevel(1);
            fallbackTask.setReasoningGoal("理解案件");
            fallbackTask.setProgressWeight(100.0);
            fallbackTask.setIsMandatory(true);
            fallbackTask.setTaskOrder(1);

            // 关键修复：设置为空列表
            fallbackTask.setPrerequisiteFragmentIds(new ArrayList<>());
            fallbackTask.setTargetKeywords(new ArrayList<>());

            InferenceTask savedTask = inferenceTaskRepository.saveAndFlush(fallbackTask);
            taskOrderToIdMap.put(1, savedTask.getTaskId());

            log.warn("已创建终极回退任务: taskId={}", savedTask.getTaskId());
        } catch (Exception ex) {
            log.error("终极回退任务创建失败", ex);
            throw new RuntimeException("无法创建任何任务", ex);
        }
    }

    // 更新海龟汤线索信息的辅助方法
    private void updateSoupWithClueInfo(HaiGuiSoup soup, List<ClueFragment> fragments) {
        List<Long> fragmentIds = fragments.stream()
                .map(ClueFragment::getFragmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        soup.setKeyClues(new Gson().toJson(fragmentIds));
        soup.setUpdatedAt(LocalDateTime.now());
    }




    // 创建海龟汤实体的辅助方法
    private HaiGuiSoup createHaiGuiSoupEntity(CreateHaiGuiSoupDTO soup) {
        UserInfo userInfo = userInfoRepository.findById(BaseContext.getCurrentId())
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        HaiGuiSoup haiGuiSoup = new HaiGuiSoup();
        haiGuiSoup.setSoupId(UUID.randomUUID().toString());
        haiGuiSoup.setPlayCount(0);
        haiGuiSoup.setUploadTime(LocalDateTime.now());
        haiGuiSoup.setCreatedAt(LocalDateTime.now());
        haiGuiSoup.setUpdatedAt(LocalDateTime.now());
        haiGuiSoup.setSoupTitle(soup.getSoupTitle());
        haiGuiSoup.setSoupSurface(soup.getSoupSurface());
        haiGuiSoup.setSoupBottom(soup.getSoupBottom());
        haiGuiSoup.setHostManual(soup.getHostManual());
        haiGuiSoup.setEstimatedDuration(soup.getEstimatedDuration());
        haiGuiSoup.setPlayerCount(soup.getPlayerCount());
        haiGuiSoup.setDifficultyLevel(soup.getDifficultyLevel());
        haiGuiSoup.setTags(soup.getTagsAsString());
        haiGuiSoup.setKeyClues("[]");
        haiGuiSoup.setUploaderId(userInfo.getUserId());
        haiGuiSoup.setCreatorId(userInfo.getUserId());
        haiGuiSoup.setUploader(userInfo);
        haiGuiSoup.setCreator(userInfo);

        return haiGuiSoup;
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

    public String uploadHaiGuiSoupAvatar(MultipartFile avatarFile, String soupId) {
        String avatarUrl = minioUtil.generateAvatarUrl(avatarFile);
        HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElseThrow
                (() -> new BusinessException(404, "故事不存在"));
        // -------------------------- 5. 更新图像到数据库 --------------------------

        // 若用户已有头像，先删除旧文件（避免占用空间）
        if (StringUtils.hasText(soup.getSoupAvatar())) {
            minioUtil.deleteAvatar(soup.getSoupAvatar());
            log.info("海龟汤图像删除成功 → 汤ID: {}, 旧URL: {}", soupId, soup.getSoupAvatar());
        }
        soup.setSoupAvatar(avatarUrl); // 存储访问URL（而非MinIO内部路径）
        haiGuiSoupRepository.save(soup);
        log.info("海龟汤头像更新成功 → 汤ID: {}, URL: {}", soup, avatarUrl);
        return avatarUrl;
    }
}