package com.guanyu.haigui.utils;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guanyu.haigui.pojo.Info.ClueFragmentInfo;
import com.guanyu.haigui.pojo.Info.InferenceTaskInfo;
import com.guanyu.haigui.pojo.result.HaiGuiInfoResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class HaiGuiInfoUtil {

    public static HaiGuiInfoResult getHaiGuiInfo(JsonNode draftFragments, JsonNode draftTasks) {
        // 处理空值情况

        // 解析线索片段（空值安全）
        List<ClueFragmentInfo> fragments = parseFragments(draftFragments);

        // 解析任务（空值安全）
        List<InferenceTaskInfo> tasks = parseTasks(draftTasks);

        return new HaiGuiInfoResult(null, fragments, tasks);
    }

    // 解析线索片段（简化版）
    private static List<ClueFragmentInfo> parseFragments(JsonNode fragmentsNode) {
        List<ClueFragmentInfo> fragments = new ArrayList<>();

        fragmentsNode = unwrapArrayNode(fragmentsNode, "fragments");
        if (fragmentsNode == null || !fragmentsNode.isArray()) {
            return fragments;
        }

        for (JsonNode node : fragmentsNode) {
            ClueFragmentInfo fragment = new ClueFragmentInfo();
            String content = firstNonBlank(
                    getText(node, "content"),
                    getText(node, "fragmentContent"),
                    getText(node, "clue"),
                    getText(node, "text")
            );
            fragment.setContent(content);
            JsonNode keywordsNode = node.has("triggerKeywords")
                    ? node.path("triggerKeywords")
                    : node.path("keywords");
            fragment.setTriggerKeywords(parseStringArray(keywordsNode));
            fragments.add(fragment);
        }

        return fragments;
    }

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


    // 解析任务（转换为实体对象）
    private static List<InferenceTaskInfo> parseTasks(JsonNode tasksNode) {
        List<InferenceTaskInfo> tasks = new ArrayList<>();

        tasksNode = unwrapArrayNode(tasksNode, "tasks", "inferenceTasks");
        if (tasksNode == null || !tasksNode.isArray()) {
            return tasks;
        }

        for (JsonNode node : tasksNode) {
            InferenceTaskInfo task = new InferenceTaskInfo();

            // 只保留需要的字段
            task.setTaskName(getText(node, "taskName"));
            task.setTaskDescription(getText(node, "taskDescription"));
            task.setTargetKeywords(parseStringArray(node.path("targetKeywords")));
            task.setReasoningGoal(getText(node, "reasoningGoal"));
            task.setProgressWeight(getDouble(node, "progressWeight"));
            task.setTaskOrder(getInt(node, "taskOrder"));

            // 修复1：解析为整数列表
            List<Integer> idList = parseIntArray(node.path("prerequisiteFragmentIds"));

            // 修复2：转换为Long列表（满足InferenceTaskInfo的要求）
            List<Long> longList = idList.stream()
                    .map(Integer::longValue)
                    .collect(Collectors.toList());

            task.setPrerequisiteFragmentIds(longList);

            tasks.add(task);
        }
        return tasks;
    }

    // 新增：解析整数数组的方法
    private static List<Integer> parseIntArray(JsonNode node) {
        List<Integer> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isNumber()) {
                    result.add(item.asInt());
                } else if (item.isTextual()) {
                    try {
                        result.add(Integer.parseInt(item.asText()));
                    } catch (NumberFormatException e) {
                        log.warn("无法解析整数字符串: {}", item.asText());
                    }
                }
            }
        }
        return result;
    }

    // 安全获取文本值
    private static String getText(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? "" : fieldNode.asText();
    }

    // 安全获取整型值
    private static Integer getInt(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? 0 : fieldNode.asInt();
    }

    // 安全获取浮点值
    private static Double getDouble(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? 0.0 : fieldNode.asDouble();
    }


    // 解析字符串数组
    private static List<String> parseStringArray(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText());
            }
        }
        return list;
    }

    private static JsonNode unwrapArrayNode(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        if (node.isObject()) {
            for (String key : keys) {
                JsonNode child = node.path(key);
                if (child.isArray()) {
                    return child;
                }
            }
        }
        return node;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}