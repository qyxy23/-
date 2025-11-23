package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.ClueType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 海龟汤线索数据模型（映射haigui_soup_clue表）
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "haigui_soup_clue",
       indexes = {
           @Index(name = "idx_soup_id", columnList = "soup_id"),
           @Index(name = "idx_clue_type", columnList = "clue_type"),
           @Index(name = "idx_is_key", columnList = "is_key")
       },
       uniqueConstraints = @UniqueConstraint(columnNames = "clue_id"))
@EqualsAndHashCode(of = "clueId")
public class SoupClue {

    @Id
    @Column(name = "clue_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String clueId;

    @Column(name = "soup_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String soupId;

    @Column(name = "clue_content", columnDefinition = "TEXT", nullable = false)
    private String clueContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "clue_type", columnDefinition = "ENUM('TIME','PLACE','CHARACTER','PLOT')", nullable = false)
    private ClueType clueType;

    @Column(name = "is_key", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isKey = true;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "is_deleted", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isDeleted = false;

    // 关联的进度任务ID列表（JSON数组）
    @Column(name = "progress_task_ids", columnDefinition = "JSON")
    private String progressTaskIds;

    // 线索向量的Redis键名
    @Column(name = "redis_key", columnDefinition = "VARCHAR(255)")
    private String redisKey;

    // 关联的海龟汤实体
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soup_id", referencedColumnName = "soup_id", foreignKey = @ForeignKey(name = "fk_soup_clue_soup"), insertable = false, updatable = false)
    private HaiGuiSoup soup;

    /**
     * 构造函数
     */
    public SoupClue(String clueId, String soupId, String clueContent, ClueType clueType, Boolean isKey) {
        this.clueId = clueId;
        this.soupId = soupId;
        this.clueContent = clueContent;
        this.clueType = clueType;
        this.isKey = isKey;
    }

    /**
     * 便利构造函数
     */
    public SoupClue(String soupId, String clueContent, ClueType clueType, Boolean isKey) {
        this(UUID.randomUUID().toString(), soupId, clueContent, clueType, isKey);
    }

    /**
     * 从GameClue转换而来
     */
    public static SoupClue fromGameClue(String soupId, GameClue gameClue) {
        SoupClue soupClue = new SoupClue();
        soupClue.setClueId(gameClue.getClueId() != null ? gameClue.getClueId() : UUID.randomUUID().toString());
        soupClue.setSoupId(soupId);
        soupClue.setClueContent(gameClue.getContent());
        soupClue.setClueType(gameClue.getClueType());
        soupClue.setIsKey(gameClue.getIsKey() != null ? gameClue.getIsKey() : true);
        return soupClue;
    }

    /**
     * 转换为GameClue
     */
    public GameClue toGameClue() {
        GameClue gameClue = new GameClue();
        gameClue.setClueId(this.clueId);
        gameClue.setContent(this.clueContent);
        gameClue.setClueType(this.clueType);
        gameClue.setIsKey(this.isKey);
        gameClue.setCreatedAt(this.createdAt != null ? this.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        return gameClue;
    }
}

