package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.model.GameClue;
import com.guanyu.haigui.pojo.result.DecompositionResult;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 线索拆解服务
 * 将汤底拆解为可向量化的线索片段并生成推理任务
 */
@Service
@Slf4j
public class ClueDecompositionService {

    private final AIManager aiManager;
    private final ObjectMapper objectMapper;
    private final BgeVectorClientUtil vectorClient;

    @Value("${haiqutang.ai.debug-mode:false}")
    private boolean debugMode;

    public ClueDecompositionService(AIManager aiManager, ObjectMapper objectMapper, BgeVectorClientUtil vectorClient) {
        this.aiManager = aiManager;
        this.objectMapper = objectMapper;
        this.vectorClient = vectorClient;
    }

    /**
     * 拆解汤底为线索片段并生成推理任务
     */
    public DecompositionResult decomposeSoupBottom(String soupTitle, String soupSurface, String soupBottom) {
        try {
            log.info("开始拆解汤底线索并生成推理任务，标题: {}，调试模式: {}", soupTitle, debugMode);

            if (debugMode) {
                // 调试模式：从aiClue.txt读取模拟数据
                return loadDebugFragments(soupTitle, soupSurface, soupBottom);
            }

            // 正常模式：调用真实AI服务
            return decomposeWithAI(soupTitle, soupSurface, soupBottom);

        } catch (Exception e) {
            log.error("拆解汤底线索失败", e);
            List<ClueFragment> fragments = generateFallbackFragments(soupTitle, soupSurface, soupBottom);
            return new DecompositionResult(fragments, new ArrayList<>());
        }
    }

    /**
     * 拆解汤底并分析用户线索为线索片段
     */
    public DecompositionResult decomposeSoupBottomWithUserClues(String soupTitle, String soupSurface, String soupBottom, List<GameClue> userClues) {
        try {
            log.info("开始拆解汤底并分析用户线索，标题: {}，用户线索数量: {}，调试模式: {}", soupTitle, userClues.size(), debugMode);

            if (debugMode) {
                // 调试模式：从aiClue.txt读取模拟数据并添加用户线索
                DecompositionResult debugResult = loadDebugFragments(soupTitle, soupSurface, soupBottom);
                List<ClueFragment> debugFragments = debugResult.getFragments();
                // 添加用户线索（模拟AI分析）
                debugFragments.addAll(analyzeUserCluesWithAI(userClues, soupTitle, soupSurface, soupBottom));
                return new DecompositionResult(debugFragments, debugResult.getInferenceTasks());
            }

            // 正常模式：调用真实AI服务同时处理汤底和用户线索
            return decomposeWithAIAndUserClues(soupTitle, soupSurface, soupBottom, userClues);

        } catch (Exception e) {
            log.error("拆解汤底并分析用户线索失败", e);
            // 失败时使用备用方案，但仍然包含用户线索的基本分析
            List<ClueFragment> fragments = generateFallbackFragments(soupTitle, soupSurface, soupBottom);
            fragments.addAll(analyzeUserCluesBasic(userClues));
            return new DecompositionResult(fragments, new ArrayList<>());
        }
    }

    /**
     * 从aiClue.txt加载调试数据
     */
    @SuppressWarnings("unchecked")
    private DecompositionResult loadDebugFragments(String soupTitle, String soupSurface, String soupBottom) {
        try {
            log.info("调试模式：从aiClue.txt加载模拟数据");

            ClassPathResource resource = new ClassPathResource("temp/aiResponse.txt");
            if (!resource.exists()) {
                log.warn("aiClue.txt文件不存在，使用备用方案");
                List<ClueFragment> fragments = generateFallbackFragments(soupTitle, soupSurface, soupBottom);
                return new DecompositionResult(fragments, new ArrayList<>());
            }

            String jsonContent = FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            log.info("读取到aiResponse.txt内容，长度: {}", jsonContent.length());

            Map<String, Object> response = objectMapper.readValue(jsonContent, Map.class);
            List<Map<String, Object>> fragmentsData = (List<Map<String, Object>>) response.get("fragments");
            List<Map<String, Object>> inferenceTasksData = (List<Map<String, Object>>) response.get("inferenceTasks");

            if (fragmentsData == null || fragmentsData.isEmpty()) {
                log.warn("aiClue.txt中未找到fragments数据，使用备用方案");
                List<ClueFragment> fragments = generateFallbackFragments(soupTitle, soupSurface, soupBottom);
                return new DecompositionResult(fragments, new ArrayList<>());
            }

            // 转换为ClueFragment对象
            List<ClueFragment> fragments = fragmentsData.stream()
                    .map(this::mapToClueFragmentWithEnhancedData)
                    .collect(Collectors.toList());

            // 为每个片段生成向量（调试模式也需要向量化以支持语义匹配）
            generateVectorsForFragments(fragments);

            log.info("调试模式：成功加载{}个模拟线索片段和{}个推理任务", fragments.size(),
                inferenceTasksData != null ? inferenceTasksData.size() : 0);
            return new DecompositionResult(fragments,
                inferenceTasksData != null ? inferenceTasksData : new ArrayList<>());
        } catch (Exception e) {
            log.error("加载调试数据失败，使用备用方案", e);
            List<ClueFragment> fragments = generateFallbackFragments(soupTitle, soupSurface, soupBottom);
            return new DecompositionResult(fragments, new ArrayList<>());
        }
    }

    /**
     * 使用AI进行拆解（正常模式）
     */
    @SuppressWarnings("unchecked")
    private DecompositionResult decomposeWithAI(String soupTitle, String soupSurface, String soupBottom) {
        try {
            log.info("正常模式：调用AI服务进行线索拆解和任务生成");

            // 生成拆解提示词
            String prompt = generateDecompositionPrompt(soupTitle, soupSurface, soupBottom);

            // 调用AI进行拆解
            String systemPrompt = "你是专业的海龟汤分析师，擅长将复杂的故事真相拆解为层次分明的线索片段并设计相应的推理任务。请严格按照JSON格式返回结果。";
            String aiResponse = aiManager.doChat(systemPrompt, prompt);

            log.info("AI返回线索拆解响应，长度: {}", aiResponse.length());

            // 解析AI响应
            Map<String, Object> response = objectMapper.readValue(aiResponse, Map.class);
            List<Map<String, Object>> fragmentsData = (List<Map<String, Object>>) response.get("fragments");
            List<Map<String, Object>> inferenceTasksData = parseInferenceTasks(response);

            // 转换为ClueFragment对象
            List<ClueFragment> fragments = fragmentsData.stream()
                    .map(this::mapToClueFragment)
                    .collect(Collectors.toList());

            // 为每个片段生成向量
            generateVectorsForFragments(fragments);

            log.info("AI模式：成功拆解出{}个线索片段和{}个推理任务", fragments.size(), inferenceTasksData.size());
            return new DecompositionResult(fragments, inferenceTasksData);

        } catch (Exception e) {
            log.error("AI拆解失败，使用备用方案", e);
            List<ClueFragment> fragments = generateFallbackFragments(soupTitle, soupSurface, soupBottom);
            return new DecompositionResult(fragments, new ArrayList<>());
        }
    }

    /**
     * 使用AI同时拆解汤底和分析用户线索（正常模式）
     */
    @SuppressWarnings("unchecked")
    private DecompositionResult decomposeWithAIAndUserClues(String soupTitle, String soupSurface, String soupBottom, List<GameClue> userClues) {
        try {
            log.info("正常模式：调用AI服务同时拆解汤底和分析用户线索，用户线索数量: {}", userClues.size());

            // 生成包含用户线索的拆解提示词
            String prompt = generateDecompositionPromptWithUserClues(soupTitle, soupSurface, soupBottom, userClues);

            // 调用AI进行拆解
            String systemPrompt = "你是专业的海龟汤分析师，擅长将复杂的故事真相拆解为层次分明的线索片段并设计相应的推理任务，并能智能分析和补充用户提供的线索信息。请严格按照JSON格式返回结果。";
            String aiResponse = aiManager.doChat(systemPrompt, prompt);

            log.info("AI返回线索拆解响应，长度: {}", aiResponse.length());

            // 解析AI响应
            Map<String, Object> response = objectMapper.readValue(aiResponse, Map.class);
            List<Map<String, Object>> fragmentsData = (List<Map<String, Object>>) response.get("fragments");
            List<Map<String, Object>> inferenceTasksData = parseInferenceTasks(response);

            // 转换为ClueFragment对象
            List<ClueFragment> fragments = fragmentsData.stream()
                    .map(this::mapToClueFragmentWithEnhancedData)
                    .collect(Collectors.toList());

            // 为每个片段生成向量
            generateVectorsForFragments(fragments);

            log.info("AI模式：成功拆解出{}个线索片段和{}个推理任务（包含{}个用户线索分析）",
                fragments.size(), inferenceTasksData.size(), userClues.size());
            return new DecompositionResult(fragments, inferenceTasksData);

        } catch (Exception e) {
            log.error("AI拆解失败，使用备用方案", e);
            List<ClueFragment> fragments = generateFallbackFragments(soupTitle, soupSurface, soupBottom);
            fragments.addAll(analyzeUserCluesBasic(userClues));
            return new DecompositionResult(fragments, new ArrayList<>());
        }
    }

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
     * 生成包含用户线索的拆解提示词
     */
    private String generateDecompositionPromptWithUserClues(String soupTitle, String soupSurface, String soupBottom, List<GameClue> userClues) {
        // 构建用户线索字符串
        StringBuilder userCluesText = new StringBuilder();
        for (int i = 0; i < userClues.size(); i++) {
            GameClue clue = userClues.get(i);
            userCluesText.append(String.format("%d. %s (类型: %s, 关键线索: %s)\n",
                i + 1,
                clue.getContent(),
                clue.getClueType().toString(),
                clue.getIsKey() ? "是" : "否"));
        }

        return String.format("""
            请将这个海龟汤的汤底（真相）拆解为多个线索片段，同时智能分析和补充用户提供的线索。

            === 基本信息 ===
            标题：%s
            汤面（玩家看到的悬念）：%s
            汤底（完整真相）：%s

            === 用户提供的线索 ===
            %s

            === 拆解要求 ===
            1. 按推理深度分层：从表层信息到深层真相
            2. 每个片段独立完整，便于向量化匹配
            3. 智能分析用户线索，为其补充以下信息：
               - 确定合适的推理层级
               - 分析线索的重要性和难度
               - 丰富线索的关键词
               - 关联相应的推理任务
               - 设计合理的相似度阈值
            4. 将用户线索与汤底拆解的其他线索整合
            5. 标注片段类型和关键信息

            === 用户线索分析要求 ===
            对每个用户线索，你需要：
            - 分析其在故事中的作用和地位
            - 确定其推理层级（1-4级）
            - 评估其重要性和难度（1-10分）
            - 补充相关的关键词，便于向量匹配
            - 判断是否为关键线索
            - 关联到合适的推理任务

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
            请严格按照以下JSON格式返回，包含汤底拆解片段、用户线索分析结果和推理任务：

            {
              "fragments": [
                {
                  "content": "线索片段的具体内容",
                  "fragmentType": "片段类型（TIME/PLACE/CHARACTER/PLOT/OBJECT/TRUTH）",
                  "inferenceLevel": 1,
                  "difficulty": 5,
                  "importance": 7,
                  "keywords": ["关键词1", "关键词2"],
                  "isCoreClue": false,
                  "similarityThreshold": 0.7,
                  "associatedTaskIds": [1, 2],
                  "order": 1,
                  "source": "AI"
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
            - 生成8-20个线索片段（包含用户线索分析）
            - 生成4-7个推理任务
            - 为每个线索片段关联合适的推理任务（使用taskOrder，如1,2,3）
            - 确保推理层次分布合理
            - 对用户线索进行智能分析和补充
            - 内容要简洁明确，便于向量匹配
            - 关键词要符合玩家提问习惯
            - 为每个片段设置合适的难度、重要性和相似度阈值
            - 明确标记来源：用户分析结果标记为"AUGMENTED_USER"
            - 推理任务要层次递进，总权重100分
            """, soupTitle, soupSurface, soupBottom, userCluesText.toString());
    }

    /**
     * 转换AI响应为ClueFragment对象
     */
    @SuppressWarnings("unchecked")
    private ClueFragment mapToClueFragment(Map<String, Object> data) {
        ClueFragment fragment = new ClueFragment();
        fragment.setFragmentContent((String) data.get("content"));
        fragment.setFragmentType((String) data.get("fragmentType"));
        fragment.setInferenceLevel((Integer) data.get("inferenceLevel"));
        fragment.setTriggerKeywords((List<String>) data.get("keywords"));
        fragment.setIsCoreClue((Boolean) data.getOrDefault("isCoreClue", false));
        fragment.setSimilarityThreshold(((Number) data.getOrDefault("similarityThreshold", 0.7)).doubleValue());

        // 根据推理层次设置默认的任务关联
        Integer inferenceLevel = (Integer) data.get("inferenceLevel");
        List<Integer> defaultTaskIds = getDefaultTaskIdsForInferenceLevel(inferenceLevel);

        fragment.setAssociatedTaskIds((List<Integer>) data.getOrDefault("associatedTaskIds", defaultTaskIds));
        fragment.setFragmentOrder((Integer) data.getOrDefault("order", 0));
        fragment.setGenerationSource("AI");
        fragment.setAiAnalysisConfidence(0.9);
        fragment.setIsDeleted(false);

        // 临时设置fragmentId为0，表示这是新对象，JPA保存后会自动生成ID
        fragment.setFragmentId(0L);

        return fragment;
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
        fragment.setAssociatedTaskIds((List<Integer>) data.getOrDefault("associatedTasks", new ArrayList<>()));
        fragment.setFragmentOrder((Integer) data.getOrDefault("order", 0));

        // 设置增强的属性
        fragment.setDifficulty(((Number) data.getOrDefault("difficulty", 2)).intValue());
        fragment.setImportance(((Number) data.getOrDefault("importance", 5)).intValue());

        String source = (String) data.getOrDefault("source", "AI");
        fragment.setGenerationSource(source);
        fragment.setAiAnalysisConfidence("AUGMENTED_USER".equals(source) ? 0.95 : 0.9);
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
                SingleEncodeResponse response = vectorClient.encodeSingle(vectorText);
                List<Double> vector = response.getEmbeddings().get(0)
                        .stream()
                        .map(Float::doubleValue)
                        .collect(java.util.stream.Collectors.toList());

                // 生成向量哈希
                String vectorHash = generateVectorHash(vector);

                fragment.setVectorData(vector);
                fragment.setVectorHash(vectorHash);

                log.debug("为片段生成向量完成: {}", fragment.getFragmentContent());

            } catch (Exception e) {
                log.error("为片段生成向量失败: {}", fragment.getFragmentContent(), e);
                // 生成空向量避免中断
                fragment.setVectorData(new ArrayList<>());
                fragment.setVectorHash("");
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
    private List<ClueFragment> generateFallbackFragments(String soupTitle, String soupSurface, String soupBottom) {
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
     * 基于AI分析用户线索（调试模式）
     */
    private List<ClueFragment> analyzeUserCluesWithAI(List<GameClue> userClues, String soupTitle, String soupSurface, String soupBottom) {
        List<ClueFragment> fragments = new ArrayList<>();

        for (int i = 0; i < userClues.size(); i++) {
            GameClue clue = userClues.get(i);

            ClueFragment fragment = new ClueFragment();
            fragment.setFragmentContent(clue.getContent());
            fragment.setFragmentType(clue.getClueType().toString());
            fragment.setInferenceLevel(1); // 用户线索默认为表层，AI会进行分析
            fragment.setIsCoreClue(clue.getIsKey());
            fragment.setFragmentOrder(i);
            fragment.setTriggerKeywords(Arrays.asList(clue.getContent().split(" ")));
            fragment.setGenerationSource("AUGMENTED_USER");
            fragment.setAiAnalysisConfidence(0.95); // 模拟AI分析的高置信度

            // 设置difficulty和importance字段的默认值
            fragment.setDifficulty(2); // 默认中等难度
            fragment.setImportance(5); // 默认中等重要性

            fragment.setSimilarityThreshold(0.8); // 用户线索相似度阈值稍高
            fragment.setAssociatedTaskIds(Arrays.asList(1, 2)); // 关联前两个推理任务

            // 向量化处理
            try {
                String keywords = fragment.getTriggerKeywords() != null ? String.join(" ", fragment.getTriggerKeywords()) : "";
                String vectorText = fragment.getFragmentContent() + " " + keywords;

                SingleEncodeResponse response = vectorClient.encodeSingle(vectorText);
                List<Double> vector = response.getEmbeddings().get(0)
                        .stream()
                        .map(Float::doubleValue)
                        .collect(java.util.stream.Collectors.toList());

                String vectorHash = generateVectorHash(vector);
                fragment.setVectorData(vector);
                fragment.setVectorHash(vectorHash);

            } catch (Exception e) {
                log.error("模拟AI分析用户线索向量化失败: {}", clue.getContent(), e);
                fragment.setVectorData(new ArrayList<>());
                fragment.setVectorHash("");
            }

            fragments.add(fragment);
        }

        log.info("模拟AI分析用户线索完成，生成{}个增强线索片段", fragments.size());
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
            fragment.setAiAnalysisConfidence(0.5); // 基本分析的置信度较低

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
            fragment.setAssociatedTaskIds(Arrays.asList(1)); // 默认关联第一个推理任务

            fragments.add(fragment);
        }

        log.info("基本分析用户线索完成，生成{}个线索片段", fragments.size());
        return fragments;
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
        List<Integer> defaultTaskIds = getDefaultTaskIdsForInferenceLevel(level);
        fragment.setAssociatedTaskIds(defaultTaskIds);

        fragment.setFragmentOrder(0);
        fragment.setGenerationSource("FALLBACK");
        fragment.setAiAnalysisConfidence(0.5);
        fragment.setVectorData(new ArrayList<>());
        fragment.setVectorHash("");
        fragment.setIsDeleted(false);
        return fragment;
    }
}