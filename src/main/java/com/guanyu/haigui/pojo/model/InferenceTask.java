package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.converter.ListStringConverter;
import com.guanyu.haigui.converter.LongSetConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Column(name = "soup_id", nullable = false, length = 36)
    private String soupId;

    @Column(name = "task_name", nullable = false, length = 100)
    private String taskName;

    @Column(name = "task_description", nullable = false, columnDefinition = "TEXT")
    private String taskDescription;

    @Column(name = "understanding_level", nullable = false)
    private Integer understandingLevel;

    @Column(name = "target_keywords", columnDefinition = "JSON")
    @Convert(converter = ListStringConverter.class)
    private List<String> targetKeywords; // 移除初始化

    @Column(name = "reasoning_goal", nullable = false, columnDefinition = "TEXT")
    private String reasoningGoal;

    @Column(name = "progress_weight", precision = 5, nullable = false)
    private Double progressWeight;

    @Column(name = "is_mandatory", columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean isMandatory = true;

    @Column(name = "task_order", nullable = false)
    private Integer taskOrder = 0;

    // 关键修复：允许为null，在@PrePersist中初始化
    @Column(name = "prerequisite_fragment_ids", columnDefinition = "JSON")
    @Convert(converter = LongSetConverter.class) // 复用Long集合转换器
    private Set<Long> prerequisiteFragmentIds = new HashSet<>(); // 初始化空集合

    @Column(name = "is_deleted", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;

        // 关键修复：在持久化前初始化集合
        if (targetKeywords == null) targetKeywords = new ArrayList<>();
        if (prerequisiteFragmentIds == null) prerequisiteFragmentIds = new HashSet<>();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}