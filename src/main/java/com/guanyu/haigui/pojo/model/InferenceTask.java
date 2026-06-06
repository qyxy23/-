package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.converter.ListLongConverter;
import com.guanyu.haigui.converter.ListStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 推理任务数据模型（精简版）
 */
@Entity
@Table(name = "hai_gui_soup_inference_task",
        indexes = {
                @Index(name = "idx_soup_id", columnList = "soup_id"),
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

    @Column(name = "reasoning_goal", nullable = false, columnDefinition = "TEXT")
    private String reasoningGoal;

    @Column(name = "progress_weight", precision = 5, scale = 2, nullable = false)
    private BigDecimal progressWeight;  // 使用BigDecimal精确表示小数

    @Column(name = "task_order", nullable = false)
    private Integer taskOrder = 0;

    @Column(name = "prerequisite_fragment_ids", columnDefinition = "JSON", nullable = false)
    @Convert(converter = ListLongConverter.class)  // 使用List<Long>替代Set<Long>
    private List<Long> prerequisiteFragmentIds = new ArrayList<>();

    @Column(name = "is_deleted", columnDefinition = "TINYINT(1) DEFAULT 0", nullable = false)
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

        if (prerequisiteFragmentIds == null) prerequisiteFragmentIds = new ArrayList<>();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 精简构造函数
    public InferenceTask(String soupId, String taskName, String taskDescription,
                         String reasoningGoal, BigDecimal progressWeight) {
        this.soupId = soupId;
        this.taskName = taskName;
        this.taskDescription = taskDescription;
        this.reasoningGoal = reasoningGoal;
        this.progressWeight = progressWeight;
        this.isDeleted = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}