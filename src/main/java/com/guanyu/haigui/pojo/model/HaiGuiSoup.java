package com.guanyu.haigui.pojo.model;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.UUID;

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
    @GeneratedValue(generator = "uuid2")
    @Column(name = "soup_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private UUID soupId;

    @Column(name = "soup_title", columnDefinition = "VARCHAR(255)")
    private String soupTitle;

    @Column(name = "soup_surface", columnDefinition = "TEXT", nullable = false)
    private String soupSurface;

    @Column(name = "soup_bottom", columnDefinition = "TEXT", nullable = false)
    private String soupBottom;

    @Column(name = "host_manual", columnDefinition = "TEXT", nullable = false)
    private String hostManual;

    @Column(name = "key_clues", columnDefinition = "JSON", nullable = false)
    private String keyClues; // 或用自定义类型（如JsonType）映射JSON数组

    @Column(name = "progress_settings", columnDefinition = "JSON", nullable = false)
    private String progressSettings; // 或用自定义类型映射JSON对象

    @Column(name = "soup_surface_vec", columnDefinition = "VARCHAR(255)")
    private String soupSurfaceVec;

    @Column(name = "soup_bottom_vec", columnDefinition = "VARCHAR(255)")
    private String soupBottomVec;

    // 创作者（关联sys_user）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", referencedColumnName = "user_id", foreignKey = @ForeignKey(name = "fk_hai_gui_soup_creator"))
    private UserInfo creator;

    // 上传者（关联sys_user）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", referencedColumnName = "user_id", foreignKey = @ForeignKey(name = "fk_hai_gui_soup_uploader"))
    private UserInfo uploader;

    @Column(name = "upload_time", columnDefinition = "DATETIME(6)", nullable = false)
    private Date uploadTime;

    @Column(name = "play_count", columnDefinition = "INT UNSIGNED", nullable = false)
    private Integer playCount;

    @Column(name = "is_deleted", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Date createdAt;

    @Column(name = "updated_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Date updatedAt;
}