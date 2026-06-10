package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import com.guanyu.haigui.converter.SoupTagConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hai_gui_soup",
        indexes = {
                @Index(name = "idx_is_deleted", columnList = "is_deleted"),
                @Index(name = "idx_created_at", columnList = "created_at"),
                @Index(name = "idx_generation_strategy", columnList = "task_generation_strategy"),
                @Index(name = "idx_difficulty_level", columnList = "difficulty_level"),
                @Index(name = "idx_estimated_duration", columnList = "estimated_duration"),
                @Index(name = "idx_player_count", columnList = "player_count"),
                @Index(name = "idx_upload_time", columnList = "upload_time"),
                @Index(name = "idx_is_published", columnList = "is_published") // 新增索引
        })
@EqualsAndHashCode(of = "soupId")
public class HaiGuiSoup {

    @Id
    @Column(name = "soup_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String soupId;

    @Column(name = "soup_title", columnDefinition = "VARCHAR(255)")
    private String soupTitle = "";

    @Column(name = "soup_surface", columnDefinition = "TEXT", nullable = false)
    private String soupSurface;

    @Column(name = "soup_bottom", columnDefinition = "TEXT", nullable = false)
    private String soupBottom;

    @Column(name = "host_manual", columnDefinition = "TEXT", nullable = false)
    private String hostManual;

    @Column(name = "ai_judge_rules", columnDefinition = "TEXT")
    private String aiJudgeRules = "";

    @Column(name = "soup_avatar", columnDefinition = "VARCHAR(255)")
    private String soupAvatar = "";

    @Column(name = "default_max_questions", columnDefinition = "INT")
    private Integer defaultMaxQuestions = 30;

    @Column(name = "task_generation_strategy", columnDefinition = "VARCHAR(20)")
    private String taskGenerationStrategy = "HYBRID";

    @Column(name = "vector_match_threshold", columnDefinition = "DECIMAL(3,2)")
    private BigDecimal vectorMatchThreshold = new BigDecimal("0.7");

    @Column(name = "creator_id", columnDefinition = "BIGINT UNSIGNED")
    private Long creatorId;

    @Column(name = "uploader_id", columnDefinition = "BIGINT UNSIGNED")
    private Long uploaderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", referencedColumnName = "user_id",
            foreignKey = @ForeignKey(name = "fk_hai_gui_soup_creator"),
            insertable = false, updatable = false)
    private UserInfo creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", referencedColumnName = "user_id",
            foreignKey = @ForeignKey(name = "fk_hai_gui_soup_uploader"),
            insertable = false, updatable = false)
    private UserInfo uploader;

    @Column(name = "upload_time", columnDefinition = "DATETIME(6)", nullable = false)
    private LocalDateTime uploadTime;

    @Column(name = "play_count", columnDefinition = "INT", nullable = false)
    private Integer playCount = 0;

    @Column(name = "estimated_duration", columnDefinition = "INT")
    private Integer estimatedDuration = 30;

    @Column(name = "player_count", columnDefinition = "INT")
    private Integer playerCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", columnDefinition = "ENUM('BEGINNER','INTERMEDIATE','ADVANCED')", nullable = false)
    private DifficultyLevel difficultyLevel = DifficultyLevel.BEGINNER;

    // 修改点1：使用String类型存储标签（对应数据库ENUM）
    @Column(name = "tags", columnDefinition = "ENUM('HORROR','HAPPY','EMOTIONAL','CREATIVE','FANTASY','DAILY','OTHER')")
    @Convert(converter = SoupTagConverter.class)
    private SoupTag tags; // 直接存储字符串值

    // 修改点2：添加是否上架字段
    @Column(name = "is_published", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isPublished = false;

    @Column(name = "is_deleted", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "DATETIME(6)", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Integer getDefaultMaxQuestions() {
        return defaultMaxQuestions != null ? defaultMaxQuestions : 30;
    }


}