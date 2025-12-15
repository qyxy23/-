package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
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
                @Index(name = "idx_key_clues", columnList = "key_clues"), // 添加JSON索引
                @Index(name = "idx_generation_strategy", columnList = "task_generation_strategy"),
                @Index(name = "idx_difficulty_level", columnList = "difficulty_level"),
                @Index(name = "idx_estimated_duration", columnList = "estimated_duration"),
                @Index(name = "idx_player_count", columnList = "player_count"),
                @Index(name = "idx_upload_time", columnList = "upload_time")
        })
@EqualsAndHashCode(of = "soupId")
public class HaiGuiSoup {

    @Id
    @Column(name = "soup_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String soupId;

    // 标题
    @Column(name = "soup_title", columnDefinition = "VARCHAR(255)")
    private String soupTitle = "";

    // 汤面
    @Column(name = "soup_surface", columnDefinition = "TEXT", nullable = false)
    private String soupSurface;

    // 汤底
    @Column(name = "soup_bottom", columnDefinition = "TEXT", nullable = false)
    private String soupBottom;

    // 主持人手册
    @Column(name = "host_manual", columnDefinition = "TEXT", nullable = false)
    private String hostManual;

    // 关键线索ID列表（JSON数组）
    @Column(name = "key_clues", columnDefinition = "JSON", nullable = false)
    private String keyClues;

    // 图像URL
    @Column(name = "soup_avatar", columnDefinition = "VARCHAR(255)")
    private String soupAvatar = "";

    // 默认最大问答次数
    @Column(name = "default_max_questions", columnDefinition = "INT")
    private Integer defaultMaxQuestions = 30;

    // 任务生成策略
    @Column(name = "task_generation_strategy", columnDefinition = "VARCHAR(20)")
    private String taskGenerationStrategy = "HYBRID";

    // 向量匹配阈值
    @Column(name = "vector_match_threshold", columnDefinition = "DECIMAL(3,2)")
    private BigDecimal vectorMatchThreshold = new BigDecimal("0.7");

    // 创作者ID（外键）
    @Column(name = "creator_id", columnDefinition = "BIGINT UNSIGNED")
    private Long creatorId;

    // 上传者ID（外键）
    @Column(name = "uploader_id", columnDefinition = "BIGINT UNSIGNED")
    private Long uploaderId;

    // 创作者（关联sys_user）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", referencedColumnName = "user_id",
            foreignKey = @ForeignKey(name = "fk_hai_gui_soup_creator"),
            insertable = false, updatable = false)
    private UserInfo creator;

    // 上传者（关联sys_user）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", referencedColumnName = "user_id",
            foreignKey = @ForeignKey(name = "fk_hai_gui_soup_uploader"),
            insertable = false, updatable = false)
    private UserInfo uploader;

    // 上传时间
    @Column(name = "upload_time", columnDefinition = "DATETIME(6)", nullable = false)
    private LocalDateTime uploadTime;

    // 游玩次数
    @Column(name = "play_count", columnDefinition = "INT", nullable = false)
    private Integer playCount = 0;

    // 预计游玩时间（分钟）
    @Column(name = "estimated_duration", columnDefinition = "INT")
    private Integer estimatedDuration = 30;

    // 游玩人数限制
    @Column(name = "player_count", columnDefinition = "INT")
    private Integer playerCount = 0;

    // 难度等级
    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", columnDefinition = "ENUM('BEGINNER','INTERMEDIATE','ADVANCED')", nullable = false)
    private DifficultyLevel difficultyLevel = DifficultyLevel.BEGINNER;

    // 海龟汤标签（JSON格式）
    @Column(name = "tags", nullable = false)
    @Enumerated(EnumType.STRING)
    private SoupTag tags;

    // 是否删除
    @Column(name = "is_deleted", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isDeleted = false;

    // 创建时间
    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    // 更新时间
    @Column(name = "updated_at", columnDefinition = "DATETIME(6)", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // 获取默认最大问答次数
    public Integer getDefaultMaxQuestions() {
        return defaultMaxQuestions != null ? defaultMaxQuestions : 30;
    }
}