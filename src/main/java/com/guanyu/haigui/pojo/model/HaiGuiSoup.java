package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hai_gui_soup",
       indexes = {
           @Index(name = "idx_is_deleted", columnList = "is_deleted"),
           @Index(name = "idx_created_at", columnList = "created_at")
       },
       uniqueConstraints = @UniqueConstraint(columnNames = "soup_id"))
@EqualsAndHashCode(of = "soupId")
public class HaiGuiSoup {

    @Id
    @Column(name = "soup_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String soupId;

    // 标题
    @Column(name = "soup_title", columnDefinition = "VARCHAR(255)")
    private String soupTitle;

    // 汤
    @Column(name = "soup_surface", columnDefinition = "TEXT", nullable = false)
    private String soupSurface;

    // 底
    @Column(name = "soup_bottom", columnDefinition = "TEXT", nullable = false)
    private String soupBottom;

    // 主持人手册
    @Column(name = "host_manual", columnDefinition = "TEXT", nullable = false)
    private String hostManual;

    // 关键线索ID列表（JSON数组）
    @Column(name = "key_clues", columnDefinition = "JSON", nullable = false)
    private String keyClues;

    
    // 汤面向量的Redis键名
    @Column(name = "soup_surface_vec", columnDefinition = "VARCHAR(255)")
    private String soupSurfaceVec;

    // 汕底向量的Redis键名
    @Column(name = "soup_bottom_vec", columnDefinition = "VARCHAR(255)")
    private String soupBottomVec;

    // 创作者ID（外键）
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    // 上传者ID（外键）
    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    // 创作者（关联sys_user）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", referencedColumnName = "user_id", foreignKey = @ForeignKey(name = "fk_hai_gui_soup_creator"), insertable = false, updatable = false)
    private UserInfo creator;

    // 上传者（关联sys_user）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", referencedColumnName = "user_id", foreignKey = @ForeignKey(name = "fk_hai_gui_soup_uploader"), insertable = false, updatable = false)
    private UserInfo uploader;

    // 上传时间
    @Column(name = "upload_time", columnDefinition = "DATETIME(6)", nullable = false)
    private LocalDateTime uploadTime;

    // 游玩次数
    @Column(name = "play_count", columnDefinition = "INT UNSIGNED", nullable = false)
    private Integer playCount;

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
}