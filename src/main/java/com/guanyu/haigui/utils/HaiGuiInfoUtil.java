package com.guanyu.haigui.utils;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        List<ClueFragmentInfo> fragments = parseFragments(draftFragments);
        List<InferenceTaskInfo> tasks = parseTasks(draftTasks);
        return new HaiGuiInfoResult(null, null, fragments, tasks);
    }

    public HaiGuiInfoResult parserHaiGuiInfo(String aiResponse) {
        try {
            String cleanedResponse = cleanAiResponseSimple(aiResponse);
            log.debug("清理后的AI响应: {}", cleanedResponse);

            ObjectMapper mapper = new ObjectMapper()
                    .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
                    .configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), true)
                    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                    .registerModule(new JavaTimeModule());

            JsonNode rootNode = mapper.readTree(cleanedResponse);

            String hostManual = extractHostManual(rootNode);
            String aiJudgeRules = extractAiJudgeRules(rootNode);
            List<ClueFragmentInfo> fragments = parseFragments(rootNode.path("fragments"));
            List<InferenceTaskInfo> tasks = parseTasks(rootNode.path("tasks"));

            log.info("成功解析响应：{}个线索片段，{}个任务", fragments.size(), tasks.size());
            return new HaiGuiInfoResult(hostManual, aiJudgeRules, fragments, tasks);

        } catch (Exception e) {
            log.error("解析AI响应失败：{}", e.getMessage(), e);
            log.debug("原始AI响应: {}", aiResponse);
            throw new RuntimeException("AI响应解析失败: " + e.getMessage(), e);
        }
    }

    public static DraftManualContent parseDraftManual(String raw) {
        DraftManualContent content = new DraftManualContent();
        if (raw == null || raw.isBlank()) {
            return content;
        }
        try {
            JsonNode node = new ObjectMapper().readTree(raw);
            content.setHostManual(firstNonBlank(
                    getText(node, "hostManual"),
                    getText(node, "content")
            ));
            content.setAiJudgeRules(getText(node, "aiJudgeRules"));
        } catch (Exception e) {
            content.setHostManual(raw);
        }
        return content;
    }

    public static String serializeDraftManual(String hostManual, String aiJudgeRules) {
        try {
            ObjectNode node = new ObjectMapper().createObjectNode();
            node.put("hostManual", hostManual != null ? hostManual : "");
            node.put("aiJudgeRules", aiJudgeRules != null ? aiJudgeRules : "");
            node.put("content", hostManual != null ? hostManual : "");
            return new ObjectMapper().writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("草稿手册序列化失败", e);
        }
    }

    private static List<ClueFragmentInfo> parseFragments(JsonNode fragmentsNode) {
        List<ClueFragmentInfo> fragments = new ArrayList<>();

        fragmentsNode = unwrapArrayNode(fragmentsNode, "fragments");
        if (fragmentsNode == null || !fragmentsNode.isArray()) {
            return fragments;
        }

        for (JsonNode node : fragmentsNode) {
            ClueFragmentInfo fragment = new ClueFragmentInfo();
            fragment.setContent(firstNonBlank(
                    getText(node, "content"),
                    getText(node, "fragmentContent"),
                    getText(node, "clue"),
                    getText(node, "text")
            ));
            fragments.add(fragment);
        }

        return fragments;
    }

    private String cleanAiResponseSimple(String aiResponse) {
        String cleaned = aiResponse.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    private String extractHostManual(JsonNode rootNode) {
        return getText(rootNode, "hostManual");
    }

    private String extractAiJudgeRules(JsonNode rootNode) {
        return getText(rootNode, "aiJudgeRules");
    }

    private static List<InferenceTaskInfo> parseTasks(JsonNode tasksNode) {
        List<InferenceTaskInfo> tasks = new ArrayList<>();

        tasksNode = unwrapArrayNode(tasksNode, "tasks", "inferenceTasks");
        if (tasksNode == null || !tasksNode.isArray()) {
            return tasks;
        }

        for (JsonNode node : tasksNode) {
            InferenceTaskInfo task = new InferenceTaskInfo();
            task.setTaskName(getText(node, "taskName"));
            task.setTaskDescription(getText(node, "taskDescription"));
            task.setReasoningGoal(getText(node, "reasoningGoal"));
            task.setProgressWeight(getDouble(node, "progressWeight"));
            task.setTaskOrder(getInt(node, "taskOrder"));

            List<Integer> idList = parseIntArray(node.path("prerequisiteFragmentIds"));
            List<Long> longList = idList.stream()
                    .map(Integer::longValue)
                    .collect(Collectors.toList());
            task.setPrerequisiteFragmentIds(longList);

            tasks.add(task);
        }
        return tasks;
    }

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

    private static String getText(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? "" : fieldNode.asText();
    }

    private static Integer getInt(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? 0 : fieldNode.asInt();
    }

    private static Double getDouble(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? 0.0 : fieldNode.asDouble();
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

    @lombok.Data
    public static class DraftManualContent {
        private String hostManual = "";
        private String aiJudgeRules = "";
    }
}
