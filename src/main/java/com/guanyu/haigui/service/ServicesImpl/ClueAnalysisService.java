package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.vo.ClueAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 线索分析服务
 * 使用AI来分析用户输入的线索，判断其类型、难度、关联任务等属性
 */
@Service
@Slf4j
public class ClueAnalysisService {

    @Autowired
    private AIManager aiManager;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 使用AI分析用户线索
     *
     * @param userClues 用户输入的线索列表
     * @param soupTitle   海龟汤标题
     * @param soupSurface 海龟汤表面
     * @param soupBottom   海龟汤汤底
     * @return AI分析后的线索结果
     */
    public List<ClueAnalysisResult> analyzeUserClues(List<String> userClues, String soupTitle, String soupSurface, String soupBottom) {
        try {
            log.info("开始AI分析用户线索: 线索数量={}", userClues.size());

            // 构建AI提示词
            String systemPrompt = buildAnalysisPrompt(soupTitle, soupSurface, soupBottom);

            // 构建用户输入
            String userPrompt = buildUserCluesPrompt(userClues);

            // 调用AI进行分析
            String aiResponse = aiManager.doChat(systemPrompt, userPrompt);

            // 解析AI响应
            return parseAIResponse(aiResponse, userClues);

        } catch (Exception e) {
            log.error("AI分析用户线索失败", e);
            // 返回默认分析结果
            return createDefaultAnalysisResults(userClues);
        }
    }

    /**
     * 构建AI系统提示词
     */
    private String buildAnalysisPrompt(String soupTitle, String soupSurface, String soupBottom) {
        return String.format("""
            你是一个海龟汤游戏的线索分析师。请分析用户提供的线索，并给出专业的分析结果。

            海龟汤信息：
            标题：%s
            表面：%s
            汤底：%s

            请对以下用户线索进行分析：
            1. 判断线索类型（时间、地点、人物、物品、情节、真相等）
            2. 评估线索难度等级（1-5级，1最简单，5最核心）
            3. 判断是否为关键线索（是否接近真相）
            4. 推断关联的推理任务层级
            5. 分析线索的重要性和优先级

            请以JSON格式返回分析结果，每条线索包含以下字段：
            - content: 线索内容
            - type: 线索类型（TIME/PLACE/CHARACTER/OBJECT/PLOT/TRUTH）
            - difficulty: 难度等级（1-5）
            - isCore: 是否关键线索（true/false）
            - associatedTask: 关联的任务层级（1-3）
            - importance: 重要性评分（1-10）
            - reasoning: 分析理由
            """,
                soupTitle, soupSurface, soupBottom);
    }

    /**
     * 构建用户线索提示词
     */
    private String buildUserCluesPrompt(List<String> userClues) {
        StringBuilder prompt = new StringBuilder("请分析以下用户提供的线索：\n\n");

        for (int i = 0; i < userClues.size(); i++) {
            prompt.append(String.format("%d. %s\n", i + 1, userClues.get(i)));
        }

        prompt.append("\n请按照前面要求的格式返回分析结果。");

        return prompt.toString();
    }

    /**
     * 解析AI响应
     */
    @SuppressWarnings("unchecked")
    private List<ClueAnalysisResult> parseAIResponse(String aiResponse, List<String> userClues) {
        try {
            // 尝试解析JSON响应
            if (aiResponse.startsWith("[")) {
                List<ClueAnalysisResult> results = objectMapper.readValue(aiResponse,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ClueAnalysisResult.class));

                // 确保返回结果数量与用户输入一致
                if (results.size() != userClues.size()) {
                    log.warn("AI分析结果数量与用户输入不一致: 输入={}, 输出={}",
                            userClues.size(), results.size());
                    // 补充默认结果
                    return ensureResultCount(userClues, results);
                }

                return results;
            } else {
                log.warn("AI响应格式异常: {}", aiResponse);
                return createDefaultAnalysisResults(userClues);
            }
        } catch (Exception e) {
            log.error("解析AI响应失败: {}", aiResponse, e);
            return createDefaultAnalysisResults(userClues);
        }
    }

    /**
     * 确保结果数量与输入一致
     */
    private List<ClueAnalysisResult> ensureResultCount(List<String> userClues, List<ClueAnalysisResult> results) {
        while (results.size() < userClues.size()) {
            ClueAnalysisResult defaultResult = createDefaultResult(userClues.get(results.size()));
            results.add(defaultResult);
        }
        return results;
    }

    /**
     * 创建默认分析结果
     */
    private List<ClueAnalysisResult> createDefaultAnalysisResults(List<String> userClues) {
        return userClues.stream()
                .map(this::createDefaultResult)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 创建默认分析结果
     */
    private ClueAnalysisResult createDefaultResult(String clueContent) {
        ClueAnalysisResult result = new ClueAnalysisResult();
        result.setContent(clueContent);
        result.setType("PLOT"); // 默认为情节
        result.setDifficulty(2); // 默认中等难度
        result.setIsCore(false);
        result.setAssociatedTask(1);
        result.setImportance(5);
        result.setReasoning("默认分析：未使用AI分析");
        return result;
    }
}