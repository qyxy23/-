package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.VectorType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.UUID;

@Data
@Entity
@Table(name = "haigui_vector_metadata",
       indexes = {
           @Index(name = "idx_soup_id", columnList = "soup_id"),
           @Index(name = "idx_vector_type", columnList = "vector_type")
       },
       uniqueConstraints = @UniqueConstraint(columnNames = "vector_id"))
@EqualsAndHashCode(of = "vectorId")
public class VectorMetadata {

    @Id
    @GeneratedValue(generator = "uuid2")
    @Column(name = "vector_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private UUID vectorId;

    // 关联海龟汤
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soup_id", referencedColumnName = "soup_id", foreignKey = @ForeignKey(name = "fk_vector_metadata_soup"))
    private HaiGuiSoup haiGuiSoup;

    @Enumerated(EnumType.STRING)
    @Column(name = "vector_type", columnDefinition = "ENUM('SURFACE','BOTTOM','MANUAL')", nullable = false)
    private VectorType vectorType;

    @Column(name = "redis_key", columnDefinition = "VARCHAR(255)", nullable = false)
    private String redisKey;

    @Column(name = "vector_dim", columnDefinition = "INT", nullable = false)
    private Integer vectorDim;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Date createdAt;

    @Column(name = "is_deleted", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isDeleted = false;
}

