package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.converter.ListStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 推理任务数据模型（映射hai_gui_soup_inference_task表）
 */
@Entity
@Table(name = "hai_gui_soup_inference_task",
       indexes = {
           @Index(name = "idx_soup_id", columnList = "soup_id"),
           @Index(name = "idx_understanding_level", columnList = "understanding_level"),
           @Index(name = "idx_task_order", columnList = "task_order"),
           @Index(name = "idx_mandatory", columnList = "is_mandatory"),
           @Index(name = "idx_is_deleted", columnList = "is_deleted")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InferenceTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskId;

    @Column(name = "soup_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String soupId;

    @Column(name = "task_name", nullable = false, columnDefinition = "VARCHAR(100)")
    private String taskName;

    @Column(name = "task_description", nullable = false, columnDefinition = "TEXT")
    private String taskDescription;

    @Column(name = "understanding_level", nullable = false, columnDefinition = "INT")
    private Integer understandingLevel;

    @Column(name = "target_keywords", columnDefinition = "JSON")
    @Convert(converter = ListStringConverter.class)
    private List<String> targetKeywords;

    @Column(name = "reasoning_goal", nullable = false, columnDefinition = "VARCHAR(255)")
    private String reasoningGoal;

    @Column(name = "progress_weight", columnDefinition = "DECIMAL(5,2)", nullable = false)
    private Double progressWeight;

    @Column(name = "is_mandatory", nullable = false, columnDefinition = "TINYINT(1)")
    private Boolean isMandatory = true;

    @Column(name = "task_order", columnDefinition = "INT")
    private Integer taskOrder = 0;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT(1)")
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 便利构造函数
    public InferenceTask(String soupId, String taskName, String taskDescription, Integer understandingLevel) {
        this.soupId = soupId;
        this.taskName = taskName;
        this.taskDescription = taskDescription;
        this.understandingLevel = understandingLevel;
        this.isDeleted = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 理解层次枚举
    public enum UnderstandingLevel {
        LEVEL_1(1, "发现表层事实"),
        LEVEL_2(2, "理解内在联系"),
        LEVEL_3(3, "推理部分真相"),
        LEVEL_4(4, "掌握核心逻辑");

        private final int level;
        private final String description;

        UnderstandingLevel(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() {
            return level;
        }

        public String getDescription() {
            return description;
        }
    }
}