package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.converter.ListDoubleConverter;
import com.guanyu.haigui.converter.ListStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 线索片段数据模型（映射hai_gui_soup_clue_fragment表）
 */
@Entity
@Table(name = "hai_gui_soup_clue_fragment",
       indexes = {
           @Index(name = "idx_soup_id", columnList = "soup_id"),
           @Index(name = "idx_fragment_type", columnList = "fragment_type"),
           @Index(name = "idx_inference_level", columnList = "inference_level"),
           @Index(name = "idx_vector_hash", columnList = "vector_hash"),
           @Index(name = "idx_core_clue", columnList = "is_core_clue"),
           @Index(name = "idx_is_deleted", columnList = "is_deleted")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClueFragment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fragmentId;

    @Column(name = "soup_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String soupId;

    @Column(name = "fragment_content", nullable = false, columnDefinition = "VARCHAR(500)")
    private String fragmentContent;

    @Column(name = "fragment_type", nullable = false, columnDefinition = "VARCHAR(50)")
    private String fragmentType;

    @Column(name = "inference_level", columnDefinition = "INT")
    private Integer inferenceLevel = 1;

    @Column(name = "vector_data", columnDefinition = "JSON")
    @Convert(converter = ListDoubleConverter.class)
    private List<Double> vectorData;

    @Column(name = "difficulty", columnDefinition = "INT")
    private Integer difficulty = 2;

    @Column(name = "importance", columnDefinition = "INT")
    private Integer importance = 5;

    @Column(name = "trigger_keywords", columnDefinition = "JSON")
    @Convert(converter = ListStringConverter.class)
    private List<String> triggerKeywords;

    @Column(name = "similarity_threshold", columnDefinition = "DECIMAL(3,2)")
    private Double similarityThreshold = 0.7;

    @Column(name = "is_core_clue", columnDefinition = "TINYINT(1)")
    private Boolean isCoreClue = false;

    @Column(name = "fragment_order", columnDefinition = "INT")
    private Integer fragmentOrder = 0;

    @Column(name = "generation_source", columnDefinition = "VARCHAR(20)")
    private String generationSource = "AI";


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
    public ClueFragment(String soupId, String fragmentContent, String fragmentType, Integer inferenceLevel) {
        this.soupId = soupId;
        this.fragmentContent = fragmentContent;
        this.fragmentType = fragmentType;
        this.inferenceLevel = inferenceLevel;
        this.isDeleted = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}