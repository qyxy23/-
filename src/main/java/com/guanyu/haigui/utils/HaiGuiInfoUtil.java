package com.guanyu.haigui.utils;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guanyu.haigui.Enum.ClueType;
import com.guanyu.haigui.pojo.Info.ClueFragmentInfo;
import com.guanyu.haigui.pojo.Info.InferenceTaskInfo;
import com.guanyu.haigui.pojo.result.HaiGuiInfoResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HaiGuiInfoUtil {

    public HaiGuiInfoResult parserHaiGuiInfo(String aiResponse) {
        try {
            // 1. 清理AI响应 - 简化版本
            String cleanedResponse = cleanAiResponseSimple(aiResponse);
            log.debug("清理后的AI响应: {}", cleanedResponse);

            // 2. 配置ObjectMapper以允许控制字符
            ObjectMapper mapper = new ObjectMapper()
                    // 替代 ALLOW_UNQUOTED_CONTROL_CHARS
                    .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
                    // 替代 ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER
                    .configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), true)
                    // 其他配置保持不变
                    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                    .registerModule(new JavaTimeModule());

            // 3. 解析JSON
            JsonNode rootNode = mapper.readTree(cleanedResponse);

            // 4. 提取主持人手册
            String hostManual = extractHostManual(rootNode);

            // 5. 解析线索片段
            List<ClueFragmentInfo> fragments = parseFragments(rootNode.path("fragments"));

            // 6. 解析任务
            List<InferenceTaskInfo> tasks = parseTasks(rootNode.path("tasks"));

            log.info("成功解析响应：{}个线索片段，{}个任务", fragments.size(), tasks.size());
            return new HaiGuiInfoResult(hostManual, fragments, tasks);

        } catch (Exception e) {
            log.error("解析AI响应失败：{}", e.getMessage(), e);
            log.debug("原始AI响应: {}", aiResponse); // 记录原始响应以便调试
            throw new RuntimeException("AI响应解析失败: " + e.getMessage(), e);
        }
    }

    // 清理AI响应中的Markdown代码块标记
    private String cleanAiResponseSimple(String aiResponse) {
        String cleaned = aiResponse.trim();

        // 处理可能的代码块标记
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7); // 移除 ```json
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3); // 移除 ```
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    // 提取主持人手册
    private String extractHostManual(JsonNode rootNode) {
        JsonNode manualNode = rootNode.path("hostManual");
        return manualNode.isMissingNode() ? "" : manualNode.asText();
    }

    // 解析线索片段
    private List<ClueFragmentInfo> parseFragments(JsonNode fragmentsNode) {
        List<ClueFragmentInfo> fragments = new ArrayList<>();
        if (fragmentsNode.isArray()) {
            for (JsonNode node : fragmentsNode) {
                ClueFragmentInfo fragment = new ClueFragmentInfo();
                fragment.setFragmentContent(getText(node, "content"));

                // 修复1: 将字符串转换为ClueType枚举
                String typeStr = getText(node, "fragmentType");
                ClueType fragmentType = convertToClueType(typeStr); // 新增转换方法
                fragment.setFragmentType(fragmentType);

                fragment.setInferenceLevel(getInt(node, "inferenceLevel"));
                fragment.setDifficulty(getInt(node, "difficulty"));
                fragment.setImportance(getInt(node, "importance"));

                // 修复2: 将Double转换为BigDecimal
                double threshold = getDouble(node, "similarityThreshold");
                fragment.setSimilarityThreshold(BigDecimal.valueOf(threshold));

                fragment.setIsCoreClue(getBoolean(node, "isCoreClue"));
                fragment.setFragmentOrder(getInt(node, "fragmentOrder"));
                fragment.setGenerationSource(getText(node, "generationSource"));
                fragment.setTriggerKeywords(parseStringArray(node.path("triggerKeywords")));
                fragments.add(fragment);
            }
        }
        return fragments;
    }

    private ClueType convertToClueType(String typeStr) {
        if (typeStr == null || typeStr.isEmpty()) {
            return ClueType.OTHER; // 默认类型
        }

        // 尝试直接匹配枚举名称（如"TIME"）
        try {
            return ClueType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 处理中文描述的情况
            return switch (typeStr) {
                case "时间" -> ClueType.TIME;
                case "地点" -> ClueType.PLACE;
                case "人物" -> ClueType.CHARACTER;
                case "角色" -> ClueType.CHARACTER; // 兼容旧数据
                case "情节" -> ClueType.PLOT;
                case "物品" -> ClueType.OBJECT;
                case "其他" -> ClueType.OTHER;
                default -> ClueType.OTHER; // 未知类型默认归为其他
            };
        }
    }

    // 解析任务（转换为实体对象）
    private List<InferenceTaskInfo> parseTasks(JsonNode tasksNode) {
        List<InferenceTaskInfo> tasks = new ArrayList<>();
        if (tasksNode.isArray()) {
            for (JsonNode node : tasksNode) {
                InferenceTaskInfo task = new InferenceTaskInfo();
                task.setTaskName(getText(node, "taskName"));
                task.setTaskDescription(getText(node, "taskDescription"));
                task.setUnderstandingLevel(getInt(node, "understandingLevel"));
                task.setTargetKeywords(parseStringArray(node.path("targetKeywords")));
                task.setReasoningGoal(getText(node, "reasoningGoal"));
                task.setProgressWeight(getDouble(node, "progressWeight"));
                task.setIsMandatory(getBoolean(node, "isMandatory"));
                task.setTaskOrder(getInt(node, "taskOrder"));

                // 转换前置线索ID
                List<Long> idList = parseLongArray(node.path("prerequisiteFragmentIds"));
                task.setPrerequisiteFragmentIds(new HashSet<>(idList));

                tasks.add(task);
            }
        }
        return tasks;
    }

    // 安全获取文本值
    private String getText(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? "" : fieldNode.asText();
    }

    // 安全获取整型值
    private Integer getInt(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? 0 : fieldNode.asInt();
    }

    // 安全获取浮点值
    private Double getDouble(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? 0.0 : fieldNode.asDouble();
    }

    // 安全获取布尔值
    private Boolean getBoolean(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return !fieldNode.isMissingNode() && fieldNode.asBoolean();
    }

    // 解析字符串数组
    private List<String> parseStringArray(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText());
            }
        }
        return list;
    }

    // 解析长整型数组
    private List<Long> parseLongArray(JsonNode node) {
        List<Long> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asLong());
            }
        }
        return list;
    }
}