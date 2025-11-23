package com.guanyu.haigui.pojo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.guanyu.haigui.Enum.ClueType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 游戏线索模型（用于业务逻辑处理，不映射数据库）
 * 这是一个业务逻辑实体，用于智能解析和处理线索数据
 * 最终会转换为JSON格式存储在HaiGuiSoup的keyClues字段中
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"createdAtAsLocalDateTime"})
public class GameClue {
    private String clueId;
    private String content;
    private ClueType clueType;
    private Integer difficulty; // 1-5，1最简单，5最难
    private Boolean isKey; // 是否为关键线索
    private String hint; // 线索提示（可选）
    private String createdAt; // 使用String存储时间，避免Jackson序列化问题

    public GameClue(String content) {
        this.content = content;
        this.clueId = UUID.randomUUID().toString();
        this.difficulty = 3; // 默认中等难度
        this.isKey = true; // 默认为关键线索
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public GameClue(String content, ClueType clueType) {
        this(content);
        this.clueType = clueType;
    }

    public GameClue(String content, ClueType clueType, Boolean isKey) {
        this(content, clueType);
        this.isKey = isKey;
    }

    /**
     * 获取线索类型的中文描述
     */
    public String getClueTypeDescription() {
        if (clueType == null) return "未知";
        switch (clueType) {
            case TIME: return "时间";
            case PLACE: return "地点";
            case CHARACTER: return "人物";
            case PLOT: return "情节";
            default: return "其他";
        }
    }

    /**
     * 获取难度描述
     */
    public String getDifficultyDescription() {
        if (difficulty == null) return "中等";
        switch (difficulty) {
            case 1: return "非常简单";
            case 2: return "简单";
            case 3: return "中等";
            case 4: return "困难";
            case 5: return "非常困难";
            default: return "中等";
        }
    }

    /**
     * 判断是否为高难度线索
     */
    public boolean isHighDifficulty() {
        return difficulty != null && difficulty >= 4;
    }

    /**
     * 判断是否为关键线索
     */
    public boolean isKeyClue() {
        return Boolean.TRUE.equals(isKey);
    }

    /**
     * 获取创建时间的LocalDateTime格式
     */
    public LocalDateTime getCreatedAtAsLocalDateTime() {
        if (createdAt == null || createdAt.isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /**
     * 设置创建时间（从LocalDateTime）
     */
    public void setCreatedAtFromLocalDateTime(LocalDateTime dateTime) {
        if (dateTime != null) {
            this.createdAt = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}