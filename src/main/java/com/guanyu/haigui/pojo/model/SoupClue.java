package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.ClueType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.UUID;

@Data
@Entity
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
    @GeneratedValue(generator = "uuid2")
    @Column(name = "clue_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private UUID clueId;

    // 关联海龟汤
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soup_id", referencedColumnName = "soup_id", foreignKey = @ForeignKey(name = "fk_soup_clue_soup"))
    private HaiGuiSoup haiGuiSoup;

    @Column(name = "clue_content", columnDefinition = "TEXT", nullable = false)
    private String clueContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "clue_type", columnDefinition = "ENUM('TIME','PLACE','CHARACTER','PLOT')", nullable = false)
    private ClueType clueType;

    @Column(name = "is_key", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isKey = true;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Date createdAt;

    @Column(name = "is_deleted", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isDeleted = false;
}

