package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.Resource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.Enum.ClueType;
import com.guanyu.haigui.Exception.AiResponseException;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.model.GameClue;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.InferenceTask;
import com.guanyu.haigui.pojo.result.DecompositionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
                fragment.setFragmentType(ClueType.valueOf(fragmentNode.get("fragmentType").asText()));
                fragment.setInferenceLevel(fragmentNode.get("inferenceLevel").asInt());
                fragment.setDifficulty(fragmentNode.get("difficulty").asInt());
                fragment.setImportance(fragmentNode.get("importance").asInt());
                fragment.setSimilarityThreshold(BigDecimal.valueOf(fragmentNode.get("similarityThreshold").asDouble()));
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



}