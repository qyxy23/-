package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.converter.ListDoubleConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 线索片段数据模型（精简版）
 */
@Entity
@Table(name = "hai_gui_soup_clue_fragment",
        indexes = {
                @Index(name = "idx_soup_id", columnList = "soup_id"),
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

    @Column(name = "vector_data", columnDefinition = "JSON")
    @Convert(converter = ListDoubleConverter.class)
    private List<Double> vectorData;  // 使用Double更精确

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

    // 精简构造函数
    public ClueFragment(String soupId, String fragmentContent) {
        this.soupId = soupId;
        this.fragmentContent = fragmentContent;
        this.isDeleted = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}