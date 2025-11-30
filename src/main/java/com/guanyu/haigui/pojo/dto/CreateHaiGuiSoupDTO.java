package com.guanyu.haigui.pojo.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import lombok.Data;

import java.util.List;

@Data
public class CreateHaiGuiSoupDTO {
    // 标题
    private String soupTitle;

    // 汤
    private String soupSurface;

    // 底
    private String soupBottom;

    // 主持人手册
    private String hostManual;

    // 汤的线索 - 支持多种格式
    // 1. 字符串：可以用分号、逗号、换行分隔多个线索
    // 2. 对象列表：完整的线索对象列表
    private Object keyClues;

    // 汤的进度设置 - 支持多种格式
    // 1. 字符串：简单描述如"困难模式"或"20回合30分钟"
    // 2. 对象：完整的进度设置对象
    private Object progressSettings;

    // 预计游玩时间（分钟）
    private Integer estimatedDuration = 30;

    // 游玩人数限制，0表示不限制，最多10人
    private Integer playerCount = 0;

    // 难度等级
    private DifficultyLevel difficultyLevel = DifficultyLevel.BEGINNER;

    // 海龟汤标签（只能选择一个）
    private SoupTag tag;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取线索字符串（向后兼容）
     */
    public String getKeyCluesAsString() {
        if (keyClues == null) {
            return "";
        }
        if (keyClues instanceof String) {
            return (String) keyClues;
        }
        try {
            // 使用ObjectMapper正确序列化对象为JSON字符串
            return objectMapper.writeValueAsString(keyClues);
        } catch (JsonProcessingException e) {
            // 如果序列化失败，对于List类型使用join方式作为fallback
            if (keyClues instanceof List) {
                return String.join("；", ((List<?>) keyClues).stream()
                        .map(Object::toString)
                        .toArray(String[]::new));
            }
            return keyClues.toString();
        }
    }

    /**
     * 获取进度设置字符串（向后兼容）
     */
    public String getProgressSettingsAsString() {
        if (progressSettings == null) {
            return "";
        }
        if (progressSettings instanceof String) {
            return (String) progressSettings;
        }
        try {
            // 使用ObjectMapper正确序列化对象为JSON字符串
            return objectMapper.writeValueAsString(progressSettings);
        } catch (JsonProcessingException e) {
            // 如果序列化失败，返回toString()作为fallback
            return progressSettings.toString();
        }
    }

    /**
     * 获取标签字符串（单个标签）
     */
    public String getTagsAsString() {
        if (tag == null) {
            return "[]";
        }
        try {
            // 将单个标签转换为描述
            String tagDescription = tag.getDescription();
            return objectMapper.writeValueAsString(java.util.List.of(tagDescription));
        } catch (JsonProcessingException e) {
            // 如果序列化失败，返回标签描述
            return "[" + tag.getDescription() + "]";
        }
    }
}
