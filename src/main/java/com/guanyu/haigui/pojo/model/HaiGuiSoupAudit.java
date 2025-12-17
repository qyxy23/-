package com.guanyu.haigui.pojo.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import com.guanyu.haigui.converter.SoupTagConverter;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hai_gui_soup_audit",
       indexes = {
           @Index(name = "idx_original_soup", columnList = "original_soup_id"),
           @Index(name = "idx_uploader", columnList = "uploader_id"),
           @Index(name = "idx_auditor", columnList = "auditor_id"),
           @Index(name = "idx_status", columnList = "audit_status"),
           @Index(name = "idx_difficulty", columnList = "difficulty_level"),
           @Index(name = "idx_duration", columnList = "estimated_duration"),
           @Index(name = "idx_player_count", columnList = "player_count")
       })
public class HaiGuiSoupAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id", columnDefinition = "BIGINT UNSIGNED")
    private Long auditId;

    @Column(name = "original_soup_id", length = 36)
    private String originalSoupId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "surface", nullable = false, columnDefinition = "TEXT")
    private String surface;

    @Column(name = "bottom", nullable = false, columnDefinition = "TEXT")
    private String bottom;

    @Column(name = "default_max_questions", columnDefinition = "INT DEFAULT 30")
    private Integer defaultMaxQuestions = 30;

    @Column(name = "estimated_duration", columnDefinition = "INT DEFAULT 30")
    private Integer estimatedDuration = 30;

    @Column(name = "player_count", columnDefinition = "INT DEFAULT 0")
    private Integer playerCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", 
           columnDefinition = "ENUM('BEGINNER','INTERMEDIATE','ADVANCED') DEFAULT 'BEGINNER'")
    private DifficultyLevel difficultyLevel = DifficultyLevel.BEGINNER;

    @Column(name = "tags", nullable = false)
    @Convert(converter = SoupTagConverter.class)
    private SoupTag tags;

    @Column(name = "uploader_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long uploaderId;


    @Enumerated(EnumType.STRING)
    @Column(name = "audit_status", 
           columnDefinition = "ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING'")
    private AuditStatus auditStatus = AuditStatus.PENDING;

    @Column(name = "auditor_id", columnDefinition = "BIGINT UNSIGNED")
    private Long auditorId;

    @Column(name = "audit_time", columnDefinition = "DATETIME(6)")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime auditTime;

    @Column(name = "audit_comment", columnDefinition = "TEXT")
    private String auditComment;

    // 拆分后的草稿字段（JSON 类型）
    @Column(columnDefinition = "JSON")
    private String draftManual; // 主持人手册（直接用字符串存储 Markdown，或用 JsonNode 包装）

    @Column(columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode draftFragments; // 线索片段（JSON 数组，用 Jackson 的 JsonNode 接收）

    @Column(columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode draftTasks; // 推理任务（JSON 数组，用 JsonNode 接收）

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;


    // 审核状态枚举
    public enum AuditStatus {
        PENDING,   // 待审核
        APPROVED,  // 审核通过
        REJECTED   // 审核未通过
    }

    // 补充内容标记（非数据库字段）
    @Transient
    private Boolean manualSupplemented = false;
    
    @Transient
    private Boolean cluesSupplemented = false;
    
    @Transient
    private Boolean tasksSupplemented = false;
}