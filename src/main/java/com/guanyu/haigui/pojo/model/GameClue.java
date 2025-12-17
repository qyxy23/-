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



    /**
     * 设置创建时间（从LocalDateTime）
     */
    public void setCreatedAtFromLocalDateTime(LocalDateTime dateTime) {
        if (dateTime != null) {
            this.createdAt = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}