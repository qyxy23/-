package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.Content.TurtleSoupEnhancedContent;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.result.DecompositionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 动态任务生成服务（混合方案版本）
 * 为每个海龟汤生成定制化的推理任务和线索拆解
 */
@Service
@Slf4j
public class DynamicTaskService {

    private final AIManager aiManager;
    private final ObjectMapper objectMapper;
    private final ClueDecompositionService clueDecompositionService;

    public DynamicTaskService(AIManager aiManager, ObjectMapper objectMapper,
                             ClueDecompositionService clueDecompositionService) {
        this.aiManager = aiManager;
        this.objectMapper = objectMapper;
        this.clueDecompositionService = clueDecompositionService;
    }

    /**
     * 为海龟汤生成完整的内容（任务+线索拆解）
     */
    public TurtleSoupEnhancedContent generateEnhancedContent(String soupTitle, String soupSurface, String soupBottom) {
        try {
            log.info("开始为海龟汤生成增强内容: {}", soupTitle);

            // 1. 拆解线索片段并生成推理任务
            DecompositionResult decompositionResult =
                clueDecompositionService.decomposeSoupBottom(soupTitle, soupSurface, soupBottom);

            List<Map<String, Object>> tasks = decompositionResult.getInferenceTasks();

            // 2. 如果AI未生成任务，则基于线索片段生成推理任务
            if (tasks == null || tasks.isEmpty()) {
                tasks = generateInferenceTasks(soupTitle, soupSurface, soupBottom, decompositionResult.getFragments());
            }

            // 3. 构建增强内容
            TurtleSoupEnhancedContent content = new TurtleSoupEnhancedContent();
            content.setSoupTitle(soupTitle);
            content.setSoupSurface(soupSurface);
            content.setSoupBottom(soupBottom);
            content.setClueFragments(decompositionResult.getFragments());
            content.setInferenceTasks(tasks);
            content.setGenerationStrategy("HYBRID");
            content.setGeneratedAt(System.currentTimeMillis());

            log.info("成功生成增强内容，包含{}个线索片段和{}个推理任务", decompositionResult.getFragments().size(), tasks.size());
            return content;

        } catch (Exception e) {
            log.error("生成增强内容失败", e);
            return generateFallbackContent(soupTitle, soupSurface, soupBottom);
        }
    }

    /**
     * 基于线索片段生成推理任务（保持向后兼容）
     */
    public List<Map<String, Object>> generateBasicTasks(String soupTitle, String soupSurface, String soupBottom) {
        TurtleSoupEnhancedContent content = generateEnhancedContent(soupTitle, soupSurface, soupBottom);
        return content.getInferenceTasks();
    }

    /**
     * 生成推理任务（基于线索片段的新方法）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> generateInferenceTasks(String soupTitle, String soupSurface, String soupBottom, List<ClueFragment> fragments) {
        try {
            log.info("开始基于线索片段生成推理任务");

            // 按推理层次分组线索片段
            Map<Integer, List<ClueFragment>> fragmentsByLevel = fragments.stream()
                    .collect(Collectors.groupingBy(ClueFragment::getInferenceLevel));

            // 生成推理任务提示词
            String prompt = generateInferenceTaskPrompt(soupTitle, soupSurface, soupBottom, fragmentsByLevel);

            // 调用AI生成任务
            String systemPrompt = "你是专业的海龟汤推理任务设计师，擅长基于线索片段设计层次分明的推理任务。请严格按照JSON格式返回结果。";
            String aiResponse = aiManager.doChat(systemPrompt, prompt);

            log.info("AI返回推理任务生成响应，长度: {}", aiResponse.length());

            // 解析AI响应
            Map<String, Object> response = objectMapper.readValue(aiResponse, Map.class);
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) response.get("inferenceTasks");

            // 为每个任务关联线索片段
            associateFragmentsWithTasks(tasks, fragments);

            return tasks;

        } catch (Exception e) {
            log.error("生成推理任务失败", e);
            // 在catch块中重新分组，避免变量作用域问题
            Map<Integer, List<ClueFragment>> fragmentsByLevel = fragments.stream()
                    .collect(Collectors.groupingBy(ClueFragment::getInferenceLevel));
            return generateFallbackInferenceTasks(soupTitle, fragmentsByLevel);
        }
    }

    /**
     * 生成推理任务提示词
     */
    private String generateInferenceTaskPrompt(String soupTitle, String soupSurface, String soupBottom,
                                             Map<Integer, List<ClueFragment>> fragmentsByLevel) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请基于提供的线索片段，为这个海龟汤设计推理任务。\n\n");

        prompt.append("=== 基本信息 ===\n");
        prompt.append(String.format("标题：%s\n", soupTitle));
        prompt.append(String.format("汤面：%s\n", soupSurface));
        prompt.append(String.format("汤底：%s\n\n", soupBottom));

        prompt.append("=== 线索片段分析 ===\n");
        fragmentsByLevel.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int level = entry.getKey();
                    List<ClueFragment> fragments = entry.getValue();
                    prompt.append(String.format("**推理层次%d**（共%d个片段）：\n", level, fragments.size()));

                    fragments.forEach(fragment -> prompt.append(String.format("- %s (%s)\n", fragment.getFragmentContent(), fragment.getFragmentType())));
                    prompt.append("\n");
                });

        prompt.append("=== 任务设计原则 ===\n");
        prompt.append("1. 层次递进：从表层发现到深层推理\n");
        prompt.append("2. 理解导向：任务完成度基于理解程度，不是提问次数\n");
        prompt.append("3. 关键词判断：设计明确的目标关键词来判断理解程度\n");
        prompt.append("4. 逻辑关联：确保任务之间有逻辑关联\n");
        prompt.append("5. 权重合理：简单任务15-25分，中等任务25-35分，困难任务35-50分\n\n");

        prompt.append("=== 输出要求 ===\n");
        prompt.append("请严格按照以下JSON格式返回：\n");
        prompt.append("{\n");
        prompt.append("  \"inferenceTasks\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"taskName\": \"推理任务名称\",\n");
        prompt.append("      \"description\": \"任务描述（要达到的理解层次）\",\n");
        prompt.append("      \"understandingLevel\": 2,\n");
        prompt.append("      \"targetKeywords\": [\"关键词1\", \"关键词2\"],\n");
        prompt.append("      \"reasoningGoal\": \"AI判断任务完成的标准\",\n");
        prompt.append("      \"progressWeight\": 30.0,\n");
        prompt.append("      \"isMandatory\": true,\n");
        prompt.append("      \"taskOrder\": 1\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");

        prompt.append("重要要求：\n");
        prompt.append("- 生成4-7个推理任务\n");
        prompt.append("- understandingLevel: 1-发现表层事实，2-理解内在联系，3-推理部分真相，4-掌握核心逻辑\n");
        prompt.append("- targetKeywords: 玩家能说出这些词才算真正理解\n");
        prompt.append("- reasoningGoal: 给AI的明确判断标准\n");
        prompt.append("- 确保所有推理层次都被覆盖\n");
        prompt.append("- 总权重值应该在100-150分之间\n");

        return prompt.toString();
    }

    /**
     * 为任务关联线索片段
     */
    private void associateFragmentsWithTasks(List<Map<String, Object>> tasks, List<ClueFragment> fragments) {
        // 这里可以实现更复杂的关联逻辑
        // 简化版本：基于推理层次关联
        for (Map<String, Object> task : tasks) {
            Integer understandingLevel = (Integer) task.get("understandingLevel");

            List<Integer> associatedFragments = fragments.stream()
                    .filter(f -> f.getInferenceLevel() <= understandingLevel)
                    .map(f -> fragments.indexOf(f) + 1) // 假设ID从1开始
                    .collect(Collectors.toList());

            task.put("requiredFragments", associatedFragments);
        }
    }

    /**
     * 生成备用推理任务
     */
    private List<Map<String, Object>> generateFallbackInferenceTasks(String soupTitle, Map<Integer, List<ClueFragment>> fragmentsByLevel) {
        log.warn("使用备用方案生成推理任务");

        List<Map<String, Object>> tasks = new ArrayList<>();

        // 基于线索片段层次生成通用任务
        if (fragmentsByLevel.containsKey(1)) {
            tasks.add(createInferenceTask("发现基本信息", "询问故事的基本背景和设定", 1,
                    Arrays.asList("什么时候", "哪里", "谁"), 20.0, 1));
        }

        if (fragmentsByLevel.containsKey(2)) {
            tasks.add(createInferenceTask("理解内在联系", "理解各要素之间的关系", 2,
                    Arrays.asList("为什么", "怎么", "关系"), 30.0, 2));
        }

        if (fragmentsByLevel.containsKey(3)) {
            tasks.add(createInferenceTask("推理深层真相", "发现隐藏的关键信息", 3,
                    Arrays.asList("真相", "秘密", "实际上"), 40.0, 3));
        }

        if (fragmentsByLevel.containsKey(4)) {
            tasks.add(createInferenceTask("掌握核心逻辑", "完全理解故事的底层逻辑", 4,
                    Arrays.asList("原来", "本质", "原理"), 50.0, 4));
        }

        return tasks;
    }

    /**
     * 创建推理任务对象
     */
    private Map<String, Object> createInferenceTask(String name, String description, int level,
                                                   List<String> keywords, double weight, int order) {
        Map<String, Object> task = new HashMap<>();
        task.put("taskName", name);
        task.put("description", description);
        task.put("understandingLevel", level);
        task.put("targetKeywords", keywords);
        task.put("reasoningGoal", "玩家能够准确表达与" + name + "相关的概念和关系");
        task.put("progressWeight", weight);
        task.put("isMandatory", level >= 3);
        task.put("taskOrder", order);
        return task;
    }

    /**
     * 生成备用内容
     */
    private TurtleSoupEnhancedContent generateFallbackContent(String soupTitle, String soupSurface, String soupBottom) {
        log.warn("使用备用方案生成增强内容");

        TurtleSoupEnhancedContent content = new TurtleSoupEnhancedContent();
        content.setSoupTitle(soupTitle);
        content.setSoupSurface(soupSurface);
        content.setSoupBottom(soupBottom);
        content.setGenerationStrategy("FALLBACK");
        content.setGeneratedAt(System.currentTimeMillis());

        // 生成基础线索片段
        List<ClueFragment> basicFragments = new ArrayList<>();
        basicFragments.add(createBasicClueFragment("故事背景", "PLACE", 1));
        basicFragments.add(createBasicClueFragment("时间设定", "TIME", 1));
        basicFragments.add(createBasicClueFragment("核心真相", "TRUTH", 4));
        content.setClueFragments(basicFragments);

        // 生成基础推理任务
        content.setInferenceTasks(generateFallbackInferenceTasks(soupTitle, new HashMap<>()));

        return content;
    }

    /**
     * 创建基础线索片段
     */
    private ClueFragment createBasicClueFragment(String content, String type, int level) {
        ClueFragment fragment = new ClueFragment();
        fragment.setFragmentContent(content);
        fragment.setFragmentType(type);
        fragment.setInferenceLevel(level);
        fragment.setTriggerKeywords(Arrays.asList(content.split(" ")));
        fragment.setIsCoreClue(level >= 3);
        fragment.setSimilarityThreshold(0.7);
        fragment.setAssociatedTaskIds(new ArrayList<>());
        fragment.setFragmentOrder(0);
        fragment.setGenerationSource("FALLBACK");
        fragment.setAiAnalysisConfidence(0.5);
        fragment.setVectorData(new ArrayList<>());
        fragment.setVectorHash("");
        return fragment;
    }

    
}

