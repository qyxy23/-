package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.Resource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.Exception.AiResponseException;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.model.GameClue;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.InferenceTask;
import com.guanyu.haigui.pojo.result.DecompositionResult;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 线索拆解服务
 * 将汤底拆解为可向量化的线索片段并生成推理任务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClueDecompositionService {

    private final AIManager aiManager;
    private final ObjectMapper objectMapper;
    private final BgeVectorClientUtil vectorClient;
    private final HaiGuiSoupRepository haiGuiSoupRepository;

    @Value("${haiqutang.ai.debug-mode:false}")
    private boolean debugMode;

    // 添加文件读取工具方法
    private String readAiResponseFromFile() throws IOException {
        Resource resource = new ClassPathResource("temp/aiResponse.txt");
        try (InputStream inputStream = resource.getStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        }
    }

    public DecompositionResult decomposeWithAIAndUserCluesAndTasks(
            HaiGuiSoup soup, List<GameClue> userClues, List<InferenceTask> userProvidedTasks
    ) {
        String soupTitle = soup.getSoupTitle();
        int soupLength = soup.getSoupBottom().length();
        String soupSurface = soup.getSoupSurface();
        String soupBottom = soup.getSoupBottom();
        int difficultyLevel = soup.getDifficultyLevel().ordinal();
        double baseFragmentCount = 8.0;
        double lengthFactor = 0.05;
        double difficultyFactor = 0.5;
        int targetFragmentCount = (int) (baseFragmentCount +
                soupLength * lengthFactor +
                difficultyLevel * difficultyFactor);
        targetFragmentCount = Math.max(8, Math.min(targetFragmentCount, 30));

        try {
            String aiResponse;
            if (!debugMode) {
                // 非调试模式：调用真实AI
                String prompt = generateDecompositionPromptWithUserCluesAndTasks(
                        soupTitle, soupSurface, soupBottom, userClues, userProvidedTasks,
                        targetFragmentCount, difficultyLevel, soupLength
                );
                String systemPrompt = "你是专业的海龟汤分析师，请严格按照JSON格式返回拆解结果，不要包含任何额外文字。";
                aiResponse = aiManager.doChat(systemPrompt, prompt);
                log.info("AI返回响应长度: {}", aiResponse.length());
            } else {
                // 调试模式：从文件读取预存响应
                aiResponse = readAiResponseFromFile();
                log.info("使用预存AI响应，长度: {}", aiResponse.length());
            }

            // 清理AI响应（共用逻辑）
            String cleanedResponse = aiResponse.trim();
            if (cleanedResponse.startsWith("```json")) {
                cleanedResponse = cleanedResponse.substring(7);
            }
            if (cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            }
            cleanedResponse = cleanedResponse.trim();

            // 解析JSON（共用逻辑）
            JsonNode rootNode = objectMapper.readTree(cleanedResponse);
            JsonNode fragmentsNode = rootNode.get("fragments");
            JsonNode tasksNode = rootNode.get("tasks");

            // 转换为ClueFragment对象
            List<ClueFragment> fragments = new ArrayList<>();
            for (JsonNode fragmentNode : fragmentsNode) {
                ClueFragment fragment = new ClueFragment();
                fragment.setSoupId(null);
                fragment.setFragmentContent(fragmentNode.get("content").asText());
                fragment.setFragmentType(fragmentNode.get("fragmentType").asText());
                fragment.setInferenceLevel(fragmentNode.get("inferenceLevel").asInt());
                fragment.setDifficulty(fragmentNode.get("difficulty").asInt());
                fragment.setImportance(fragmentNode.get("importance").asInt());
                fragment.setSimilarityThreshold(fragmentNode.get("similarityThreshold").asDouble());
                fragment.setIsCoreClue(fragmentNode.get("isCoreClue").asBoolean());
                fragment.setFragmentOrder(fragmentNode.get("fragmentOrder").asInt());
                fragment.setGenerationSource(fragmentNode.get("generationSource").asText());
                fragment.setVectorData(new ArrayList<>());
                fragment.setTriggerKeywords(parseJsonStringArray(fragmentNode.get("triggerKeywords")));
                fragments.add(fragment);
            }

            // 转换为InferenceTask对象
            List<Map<String, Object>> tasks = new ArrayList<>();
            for (JsonNode taskNode : tasksNode) {
                Map<String, Object> task = new HashMap<>();
                task.put("taskName", taskNode.get("taskName").asText());
                task.put("taskDescription", taskNode.get("taskDescription").asText());
                task.put("understandingLevel", taskNode.get("understandingLevel").asInt());
                task.put("targetKeywords", parseJsonStringArray(taskNode.get("targetKeywords")));
                task.put("reasoningGoal", taskNode.get("reasoningGoal").asText());
                task.put("progressWeight", taskNode.get("progressWeight").asDouble());
                task.put("isMandatory", taskNode.get("isMandatory").asBoolean());
                task.put("taskOrder", taskNode.get("taskOrder").asInt());
                task.put("prerequisiteFragmentIds", parseJsonIntArray(taskNode.get("prerequisiteFragmentIds")));
                tasks.add(task);
            }

            log.info("成功解析响应：{}个线索片段，{}个任务", fragments.size(), tasks.size());
            return new DecompositionResult(fragments, tasks);

        } catch (Exception e) {
            log.error("拆解失败", e);
            throw new AiResponseException("拆解失败");
        }
    }

    // 辅助方法：解析JSON字符串数组
    private List<String> parseJsonStringArray(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText());
            }
        }
        return list;
    }

    // 辅助方法：解析JSON整数数组
    private List<Integer> parseJsonIntArray(JsonNode node) {
        List<Integer> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asInt());
            }
        }
        return list;
    }

    private List<Long> parseJsonArray(String json) {
        try {
            String clean = json.replace("[", "").replace("]", "").replace(" ", "");
            if (clean.isEmpty()) return Collections.emptyList();

            return Arrays.stream(clean.split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("解析数组失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    // private void saveAIAnalysisResults(String soupId, double estimatedDifficulty, int recommendedFragmentCount) {
    //     // 根据标题找到海龟汤实体并更新
    //     HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElse(null);
    //     if (soup!=null) {
    //         soup.setAiEstimatedDifficulty(estimatedDifficulty);
    //         haiGuiSoupRepository.save(soup);
    //     }
    // }


    /**
     * 使用AI进行拆解（正常模式）
     */
    // @SuppressWarnings("unchecked")
    // private DecompositionResult decomposeWithAI(String soupTitle, String soupSurface, String soupBottom) {
    //     try {
    //         log.info("正常模式：调用AI服务进行线索拆解和任务生成");
    //
    //         // 生成拆解提示词
    //         String prompt = generateDecompositionPrompt(soupTitle, soupSurface, soupBottom);
    //
    //         // 调用AI进行拆解
    //         String systemPrompt = "你是专业的海龟汤分析师，擅长将复杂的故事真相拆解为层次分明的线索片段并设计相应的推理任务。请严格按照JSON格式返回结果。";
    //         String aiResponse = aiManager.doChat(systemPrompt, prompt);
    //
    //         log.info("AI返回线索拆解响应，长度: {}", aiResponse.length());
    //
    //         // 解析AI响应
    //         Map<String, Object> response = objectMapper.readValue(aiResponse, Map.class);
    //         List<Map<String, Object>> fragmentsData = (List<Map<String, Object>>) response.get("fragments");
    //         List<Map<String, Object>> inferenceTasksData = parseInferenceTasks(response);
    //
    //         // 转换为ClueFragment对象
    //         List<ClueFragment> fragments = fragmentsData.stream()
    //                 .map(this::mapToClueFragment)
    //                 .collect(Collectors.toList());
    //
    //         // 为每个片段生成向量
    //         generateVectorsForFragments(fragments);
    //
    //         log.info("AI模式：成功拆解出{}个线索片段和{}个推理任务", fragments.size(), inferenceTasksData.size());
    //         return new DecompositionResult(fragments, inferenceTasksData);
    //
    //     } catch (Exception e) {
    //         log.error("AI拆解失败，使用备用方案", e);
    //         List<ClueFragment> fragments = generateFallbackFragments();
    //         return new DecompositionResult(fragments, new ArrayList<>());
    //     }
    // }


    /**
     * 生成拆解提示词
     */
    private String generateDecompositionPrompt(String soupTitle, String soupSurface, String soupBottom) {
        return String.format("""
                请将这个海龟汤的汤底（真相）拆解为多个线索片段，每个片段代表一个推理层次的信息。
                
                === 基本信息 ===
                标题：%s
                汤面（玩家看到的悬念）：%s
                汤底（完整真相）：%s
                
                === 拆解要求 ===
                1. 按推理深度分层：从表层信息到深层真相
                2. 每个片段独立完整，便于向量化匹配
                3. 标注片段类型和关键信息
                4. 设计合理的匹配阈值
                5. 关联相应的推理任务
                
                === 推理层次说明 ===
                **层次1-表层信息**：直接观察到的事实
                **层次2-中层推理**：需要简单推理才能发现的信息
                **层次3-深层真相**：隐藏的关键真相
                **层次4-核心逻辑**：整个故事的底层逻辑
                
                === 片段类型说明 ===
                **TIME**：时间相关（年代、时期、时间跨度）
                **PLACE**：地点相关（具体位置、环境特征）
                **CHARACTER**：人物相关（身份、关系、数量）
                **PLOT**：情节相关（事件、过程、结果）
                **OBJECT**：物品相关（关键物品、道具）
                **TRUTH**：真相相关（核心秘密、深层逻辑）
                
                === 输出要求 ===
                请严格按照以下JSON格式返回，同时包含线索片段和推理任务：
                
                {
                  "fragments": [
                    {
                      "content": "线索片段的具体内容",
                      "fragmentType": "片段类型（TIME/PLACE/CHARACTER/PLOT/OBJECT/TRUTH）",
                      "inferenceLevel": 1,
                      "keywords": ["关键词1", "关键词2"],
                      "isCoreClue": false,
                      "similarityThreshold": 0.7,
                      "associatedTaskIds": [1, 2],
                      "order": 1
                    }
                  ],
                  "inferenceTasks": [
                    {
                      "taskName": "推理任务名称",
                      "description": "任务描述（要达到的理解层次）",
                      "understandingLevel": 1,
                      "targetKeywords": ["关键词1", "关键词2"],
                      "reasoningGoal": "AI判断任务完成的标准",
                      "progressWeight": 20.0,
                      "isMandatory": true,
                      "taskOrder": 1
                    }
                  ]
                }
                
                重要要求：
                - 生成8-15个线索片段
                - 生成4-7个推理任务
                - 为每个线索片段关联合适的推理任务（使用taskOrder，如1,2,3）
                - 确保推理层次分布合理
                - 内容要简洁明确，便于向量匹配
                - 关键词要符合玩家提问习惯
                - 为每个片段设置合适的相似度阈值
                - 推理任务要层次递进，总权重100-150分
                """, soupTitle, soupSurface, soupBottom);
    }


    /**
     * 根据推理层次获取默认的任务关联ID
     */
    private List<Integer> getDefaultTaskIdsForInferenceLevel(Integer inferenceLevel) {
        List<Integer> taskIds = new ArrayList<>();

        // 根据推理层次关联到对应的任务
        // inferenceLevel 1-4 对应 taskOrder 1-3
        if (inferenceLevel != null && inferenceLevel >= 1 && inferenceLevel <= 4) {
            // 将推理层次映射到任务顺序
            int taskOrder = Math.min(inferenceLevel, 3); // 最多3个任务
            taskIds.add(taskOrder);
        }

        return taskIds;
    }

    /**
     * 解析AI响应中的推理任务
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseInferenceTasks(Map<String, Object> response) {
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) response.get("inferenceTasks");
        return tasks != null ? tasks : new ArrayList<>();
    }

    /**
     * 转换AI响应为包含增强数据的ClueFragment对象
     */
    @SuppressWarnings("unchecked")
    private ClueFragment mapToClueFragmentWithEnhancedData(Map<String, Object> data) {
        ClueFragment fragment = new ClueFragment();
        fragment.setFragmentContent((String) data.get("content"));
        fragment.setFragmentType((String) data.get("fragmentType"));
        fragment.setInferenceLevel((Integer) data.get("inferenceLevel"));
        fragment.setTriggerKeywords((List<String>) data.get("keywords"));
        fragment.setIsCoreClue((Boolean) data.getOrDefault("isCoreClue", false));
        fragment.setSimilarityThreshold(((Number) data.getOrDefault("similarityThreshold", 0.7)).doubleValue());
        fragment.setFragmentOrder((Integer) data.getOrDefault("order", 0));

        // 设置增强的属性
        fragment.setDifficulty(((Number) data.getOrDefault("difficulty", 2)).intValue());
        fragment.setImportance(((Number) data.getOrDefault("importance", 5)).intValue());

        String source = (String) data.getOrDefault("source", "AI");
        fragment.setGenerationSource(source);
        fragment.setIsDeleted(false);

        // 临时设置fragmentId为0，表示这是新对象，JPA保存后会自动生成ID
        fragment.setFragmentId(0L);

        return fragment;
    }

    /**
     * 为线索片段生成向量
     */
    private void generateVectorsForFragments(List<ClueFragment> fragments) {
        for (ClueFragment fragment : fragments) {
            try {
                // 生成向量化文本（内容+关键词）
                String keywords = fragment.getTriggerKeywords() != null ? String.join(" ", fragment.getTriggerKeywords()) : "";
                String vectorText = fragment.getFragmentContent() + " " + keywords;

                // 调用BGE向量服务
                SingleEncodeResponse response = BgeVectorClientUtil.encodeSingle(vectorText);
                List<Float> vector = response.getEmbeddings().get(0);

                // 生成向量哈希
                // String vectorHash = generateVectorHash(vector);

                fragment.setVectorData(vector);

                log.debug("为片段生成向量完成: {}", fragment.getFragmentContent());

            } catch (Exception e) {
                log.error("为片段生成向量失败: {}", fragment.getFragmentContent(), e);
                // 生成空向量避免中断
                fragment.setVectorData(new ArrayList<>());
            }
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
     * 生成备用线索片段（当AI失败时）
     */
    private List<ClueFragment> generateFallbackFragments() {
        log.warn("使用备用方案生成线索片段");

        // 基于汤底内容生成通用片段
        List<ClueFragment> fragments = new ArrayList<>();

        // 添加基本信息片段
        fragments.add(createBasicFragment("故事背景", "PLACE", 1, 0.8));
        fragments.add(createBasicFragment("时间设定", "TIME", 1, 0.8));
        fragments.add(createBasicFragment("主要人物", "CHARACTER", 2, 0.7));
        fragments.add(createBasicFragment("关键事件", "PLOT", 2, 0.7));
        fragments.add(createBasicFragment("核心真相", "TRUTH", 4, 0.9));

        return fragments;
    }


    /**
     * 基本分析用户线索（当AI失败时）
     */
    private List<ClueFragment> analyzeUserCluesBasic(List<GameClue> userClues) {
        List<ClueFragment> fragments = new ArrayList<>();

        for (int i = 0; i < userClues.size(); i++) {
            GameClue clue = userClues.get(i);

            ClueFragment fragment = new ClueFragment();
            fragment.setFragmentContent(clue.getContent());
            fragment.setFragmentType(clue.getClueType().toString());
            fragment.setInferenceLevel(1); // 默认为表层信息
            fragment.setIsCoreClue(clue.getIsKey());
            fragment.setFragmentOrder(i);
            fragment.setTriggerKeywords(Arrays.asList(clue.getContent().split(" ")));
            fragment.setGenerationSource("USER_BASIC");

            // 基本分析设置默认值
            if (clue.getIsKey()) {
                // 关键线索设置更高的重要性
                fragment.setDifficulty(3); // 关键线索难度中高
                fragment.setImportance(8); // 关键线索重要性高
            } else {
                fragment.setDifficulty(2); // 普通线索难度中等
                fragment.setImportance(5); // 普通线索重要性中等
            }

            fragment.setSimilarityThreshold(0.7); // 默认相似度阈值

            fragments.add(fragment);
        }

        log.info("基本分析用户线索完成，生成{}个线索片段", fragments.size());
        return fragments;
    }

    // /**
    //  * 使用AI同时拆解汤底和分析用户线索，并使用用户提供的任务列表（正常模式）
    //  */
    // @SuppressWarnings("unchecked")
    // private DecompositionResult decomposeWithAIAndUserCluesAndTasks(String soupTitle, String soupSurface, String soupBottom, List<GameClue> userClues, List<InferenceTask> userProvidedTasks) {
    //     try {
    //         log.info("正常模式：调用AI服务同时拆解汤底和分析用户线索，使用用户任务列表，用户线索数量: {}, 用户任务数量: {}",
    //             userClues.size(), userProvidedTasks != null ? userProvidedTasks.size() : 0);
    //
    //         // 生成包含用户线索和用户任务的拆解提示词
    //         String prompt = generateDecompositionPromptWithUserCluesAndTasks(soupTitle, soupSurface, soupBottom, userClues, userProvidedTasks);
    //
    //         // 调用AI进行拆解
    //         String systemPrompt = "你是专业的海龟汤分析师，擅长将复杂的故事真相拆解为层次分明的线索片段，并能智能分析和补充用户提供的线索信息。请严格按照JSON格式返回结果。";
    //         String aiResponse = aiManager.doChat(systemPrompt, prompt);
    //
    //         log.info("AI返回线索拆解响应，长度: {}", aiResponse.length());
    //
    //         // 解析AI响应
    //         Map<String, Object> response = objectMapper.readValue(aiResponse, Map.class);
    //         List<Map<String, Object>> fragmentsData = (List<Map<String, Object>>) response.get("fragments");
    //
    //         // 转换为ClueFragment对象
    //         List<ClueFragment> fragments = fragmentsData.stream()
    //                 .map(this::mapToClueFragmentWithEnhancedData)
    //                 .collect(Collectors.toList());
    //
    //         // 为每个片段生成向量
    //         generateVectorsForFragments(fragments);
    //
    //         // 返回AI生成的片段和用户提供的任务
    //         List<Map<String, Object>> userTasksAsMap = convertUserTasksToMap(userProvidedTasks);
    //
    //         log.info("AI模式：成功拆解出{}个线索片段（包含{}个用户线索分析），使用{}个用户任务",
    //             fragments.size(), userClues.size(), userProvidedTasks != null ? userProvidedTasks.size() : 0);
    //         return new DecompositionResult(fragments, userTasksAsMap);
    //
    //     } catch (Exception e) {
    //         log.error("AI拆解失败，使用备用方案", e);
    //         List<ClueFragment> fragments = generateFallbackFragments();
    //         fragments.addAll(analyzeUserCluesBasic(userClues));
    //         List<Map<String, Object>> userTasksAsMap = convertUserTasksToMap(userProvidedTasks);
    //         return new DecompositionResult(fragments, userTasksAsMap);
    //     }
    // }

    public String generateDecompositionPromptWithUserCluesAndTasks(
            String soupTitle, String soupSurface, String soupBottom,
            List<GameClue> userClues, List<InferenceTask> userProvidedTasks,
            int targetFragmentCount, int difficultyLevel, int soupLength
    ) {
        // 1. 构建用户线索字符串
        StringBuilder userCluesText = new StringBuilder();
        for (int i = 0; i < userClues.size(); i++) {
            GameClue clue = userClues.get(i);
            userCluesText.append(String.format("%d. %s (类型: %s, 关键线索: %s)\n",
                    i + 1, clue.getContent(), clue.getClueType(), clue.getIsKey() ? "是" : "否"));
        }

        // 2. 构建用户任务字符串
        StringBuilder userTasksText = new StringBuilder();
        if (userProvidedTasks != null && !userProvidedTasks.isEmpty()) {
            for (int i = 0; i < userProvidedTasks.size(); i++) {
                InferenceTask task = userProvidedTasks.get(i);
                userTasksText.append(String.format("任务%d: %s (taskOrder: %d, description: %s, understandingLevel: %d)\n",
                        i + 1, task.getTaskName(), task.getTaskOrder(), task.getTaskDescription(), task.getUnderstandingLevel()));
            }
        } else {
            userTasksText.append("无用户提供的任务");
        }

        // 3. 难度描述和系数计算
        String difficultyDesc = switch (difficultyLevel) {
            case 1 -> "入门（1星）";
            case 2 -> "中等（2星）";
            case 3 -> "困难（3星）";
            default -> "未知";
        };
        double difficultyCoefficient = difficultyLevel == 1 ? 1.0 : difficultyLevel == 2 ? 1.2 : 1.4;

        // 4. 生成修改后的提示词（支持任务独立解锁）
        return String.format("""
你是专业的海龟汤分析师，请按以下要求拆解汤底并分析用户线索。**请严格按照指定的JSON格式返回结果**。

=== 基本信息 ===
标题：%s
汤面：%s
汤底：%s
汤底长度：%d字符
难度级别：%s（用户创建时选择）
目标线索数量：%d条（计算：(%d÷100)×%.1f=%.1f→%d条）

=== 用户提供的线索 ===
%s

=== 用户提供的任务 ===
%s

=== 拆解要求 ===
1. 线索数量：严格控制在%d±2条范围内
2. 任务数量：严格控制在3-5个（根据汤底复杂度合理分配）
3. 每个线索片段必须包含以下字段（与数据库表hai_gui_soup_clue_fragment对应）：
   - content: 线索内容（字符串，最多500字符）
   - fragmentType: 片段类型（TIME/PLACE/CHARACTER/PLOT/OBJECT/TRUTH）
   - inferenceLevel: 推理深度（1-4）
   - difficulty: 线索难度（1-5，默认2）
   - importance: 线索重要性（1-10，默认5）
   - similarityThreshold: 相似度阈值（0.1-1.0，默认0.7）
   - isCoreClue: 是否核心线索（true/false，默认false）
   - fragmentOrder: 片段顺序（唯一整数ID，从1开始）
   - generationSource: 生成来源（"AI"）
   - triggerKeywords: 触发关键词数组（如["时间","夜晚"]）

4. 每个推理任务必须包含以下字段（与数据库表hai_gui_soup_inference_task对应）：
   - taskName: 任务名称（字符串）
   - taskDescription: 任务描述（字符串）
   - understandingLevel: 理解层次（1-4）
   - targetKeywords: 目标关键词数组（如["时间异常","身份替换"]）
   - reasoningGoal: 推理目标（字符串）
   - progressWeight: 进度权重（数字，**所有任务权重总和必须为100**）
   - isMandatory: 是否必做（true/false，默认true）
   - taskOrder: 任务顺序（唯一整数ID）
   - prerequisiteFragmentIds: 前置线索ID数组（**必须填写**，用fragmentOrder作为ID，如[1,3]）

=== 任务解锁规则 ===
- **任务之间相互独立**：玩家可以自由探索线索，无需按顺序完成任务
- **任务解锁条件**：只需获得该任务指定的前置线索（prerequisiteFragmentIds）即可解锁
- **示例**：任务B只需线索3和5，即使任务A未完成也可解锁

=== 输出格式要求 ===
请返回严格的JSON格式，**不要包含任何其他文字**：

{
  "fragments": [
    {
      "content": "线索片段内容",
      "fragmentType": "TIME",
      "inferenceLevel": 1,
      "difficulty": 2,
      "importance": 5,
      "similarityThreshold": 0.7,
      "isCoreClue": false,
      "fragmentOrder": 1,
      "generationSource": "AI",
      "triggerKeywords": ["关键词1", "关键词2"]
    }
  ],
  "tasks": [
    {
      "taskName": "任务名称",
      "taskDescription": "任务描述",
      "understandingLevel": 2,
      "targetKeywords": ["关键词1", "关键词2"],
      "reasoningGoal": "推理目标描述",
      "progressWeight": 30.0,
      "isMandatory": true,
      "taskOrder": 1,
      "prerequisiteFragmentIds": [1, 3]
    }
  ]
}

=== 重要提醒 ===
- fragmentOrder在fragments中必须唯一（1,2,3...）
- taskOrder在tasks中必须唯一（1,2,3...）
- **prerequisiteFragmentIds必须填写，不可省略**
- **每个任务只需依赖特定线索，不依赖其他任务**
- **任务之间相互独立，玩家可自由探索**
- **任务数量严格控制在3-5个**
- **所有任务的progressWeight总和必须为100**
- 所有字段名必须与上述格式完全一致
- 返回纯JSON，不要加任何说明文字
- **前置线索ID必须使用fragmentOrder的值**
""",
                soupTitle, soupSurface, soupBottom, soupLength, difficultyDesc,
                targetFragmentCount, soupLength, difficultyCoefficient,
                (soupLength/100.0) * difficultyCoefficient, targetFragmentCount,
                userCluesText, userTasksText,
                targetFragmentCount);
    }


    /**
     * 将用户提供的线索列表格式化为文本
     */
    private String buildUserCluesText(List<GameClue> userClues) {
        if (userClues == null || userClues.isEmpty()) {
            return "用户没有提供线索。";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < userClues.size(); i++) {
            GameClue clue = userClues.get(i);
            sb.append(String.format("%d. [%s] %s (关键：%s)\n",
                    i + 1,
                    clue.getClueType(),
                    clue.getContent(),
                    clue.getIsKey() ? "是" : "否"));
        }
        return sb.toString();
    }

    /**
     * 将用户提供的任务列表格式化为文本
     */
    private String buildUserTasksText(List<InferenceTask> userTasks) {
        if (userTasks == null || userTasks.isEmpty()) {
            return "用户没有提供任务。";
        }

        StringBuilder sb = new StringBuilder();
        for (InferenceTask task : userTasks) {
            sb.append(String.format("%d. %s\n   描述：%s\n   权重：%.1f\n\n",
                    task.getTaskOrder(),
                    task.getTaskName(),
                    task.getTaskDescription(),
                    task.getProgressWeight()));
        }
        return sb.toString();
    }


    /**
     * 将用户提供的任务列表转换为Map格式
     */
    private List<Map<String, Object>> convertUserTasksToMap(List<InferenceTask> userProvidedTasks) {
        if (userProvidedTasks == null || userProvidedTasks.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> taskMaps = new ArrayList<>();
        for (InferenceTask task : userProvidedTasks) {
            Map<String, Object> taskMap = new HashMap<>();
            taskMap.put("taskName", task.getTaskName());
            taskMap.put("description", task.getTaskDescription());
            taskMap.put("understandingLevel", task.getUnderstandingLevel());
            taskMap.put("targetKeywords", task.getTargetKeywords() != null ? task.getTargetKeywords() : new ArrayList<>());
            taskMap.put("reasoningGoal", task.getReasoningGoal());
            taskMap.put("progressWeight", task.getProgressWeight());
            taskMap.put("isMandatory", task.getIsMandatory());
            taskMap.put("taskOrder", task.getTaskOrder());
            taskMaps.add(taskMap);
        }
        return taskMaps;
    }

    /**
     * 创建基础线索片段
     */
    private ClueFragment createBasicFragment(String content, String type, int level, double threshold) {
        ClueFragment fragment = new ClueFragment();
        fragment.setFragmentContent(content);
        fragment.setFragmentType(type);
        fragment.setInferenceLevel(level);
        fragment.setTriggerKeywords(Arrays.asList(content.split(" ")));
        fragment.setIsCoreClue(level >= 3);
        fragment.setSimilarityThreshold(threshold);

        // 根据推理层次设置默认的任务关联

        fragment.setFragmentOrder(0);
        fragment.setGenerationSource("FALLBACK");
        fragment.setVectorData(new ArrayList<>());
        fragment.setIsDeleted(false);
        return fragment;
    }
}