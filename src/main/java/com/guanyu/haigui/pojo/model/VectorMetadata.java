package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.VectorType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 向量元数据模型（映射haigui_vector_metadata表）
 * 用于记录Redis Stack中存储的向量数据的元信息
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "haigui_vector_metadata",
       indexes = {
           @Index(name = "idx_soup_id", columnList = "soup_id"),
           @Index(name = "idx_vector_type", columnList = "vector_type")
       },
       uniqueConstraints = @UniqueConstraint(columnNames = "vector_id"))
@EqualsAndHashCode(of = "vectorId")
public class VectorMetadata {

    @Id
    @Column(name = "vector_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String vectorId;

    @Column(name = "soup_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String soupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vector_type", columnDefinition = "ENUM('SURFACE','BOTTOM','MANUAL','CLUE')", nullable = false)
    private VectorType vectorType;

    @Column(name = "redis_key", columnDefinition = "VARCHAR(255)", nullable = false)
    private String redisKey;

    @Column(name = "vector_dim", columnDefinition = "INT", nullable = false)
    private Integer vectorDim;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "is_deleted", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isDeleted = false;

    // 关联的海龟汤实体
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soup_id", referencedColumnName = "soup_id", foreignKey = @ForeignKey(name = "fk_vector_metadata_soup"), insertable = false, updatable = false)
    private HaiGuiSoup soup;

    /**
     * 构造函数
     */
    public VectorMetadata(String vectorId, String soupId, VectorType vectorType, String redisKey, Integer vectorDim) {
        this.vectorId = vectorId;
        this.soupId = soupId;
        this.vectorType = vectorType;
        this.redisKey = redisKey;
        this.vectorDim = vectorDim;
    }

    /**
     * 便利构造函数（自动生成vectorId）
     */
    public VectorMetadata(String soupId, VectorType vectorType, String redisKey, Integer vectorDim) {
        this(UUID.randomUUID().toString(), soupId, vectorType, redisKey, vectorDim);
    }

    /**
     * 生成Redis键名的工具方法
     * @param soupId 海龟汤ID
     * @param vectorType 向量类型
     * @param clueId 线索ID（仅当vectorType为CLUE时需要）
     * @return Redis键名
     */
    public static String generateRedisKey(String soupId, VectorType vectorType, String clueId) {
        switch (vectorType) {
            case SURFACE:
                return String.format("hai_gui:vec:surface:%s", soupId);
            case BOTTOM:
                return String.format("hai_gui:vec:bottom:%s", soupId);
            case MANUAL:
                return String.format("hai_gui:vec:manual:%s", soupId);
            case CLUE:
                return String.format("hai_gui:vec:clue:%s", clueId != null ? clueId : soupId);
            default:
                return String.format("hai_gui:vec:unknown:%s", soupId);
        }
    }

    /**
     * 生成线索的Redis键名
     * @param clueId 线索ID
     * @return Redis键名
     */
    public static String generateClueRedisKey(String clueId) {
        return String.format("hai_gui:vec:clue:%s", clueId);
    }
}

