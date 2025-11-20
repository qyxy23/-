package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.UUID;

@Data
@Entity
@Table(name = "haigui_player_progress",
       indexes = {
           @Index(name = "idx_session_id", columnList = "session_id"),
           @Index(name = "idx_soup_id", columnList = "soup_id"),
           @Index(name = "idx_progress_key", columnList = "progress_key")
       },
       uniqueConstraints = @UniqueConstraint(columnNames = "progress_id"))
@EqualsAndHashCode(of = "progressId")
public class PlayerProgress {

    @Id
    @GeneratedValue(generator = "uuid2")
    @Column(name = "progress_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private UUID progressId;

    // 关联游戏会话
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", referencedColumnName = "session_id", foreignKey = @ForeignKey(name = "fk_player_progress_session"))
    private GameSession gameSession;

    // 关联海龟汤
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soup_id", referencedColumnName = "soup_id", foreignKey = @ForeignKey(name = "fk_player_progress_soup"))
    private HaiGuiSoup haiGuiSoup;

    // 关联线索（可选）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clue_id", referencedColumnName = "clue_id", foreignKey = @ForeignKey(name = "fk_player_progress_clue"))
    private SoupClue soupClue;

    @Column(name = "progress_key", columnDefinition = "VARCHAR(50)", nullable = false)
    private String progressKey;

    @Column(name = "progress_value", columnDefinition = "DECIMAL(5,2)", nullable = false)
    private Double progressValue;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Date createdAt;

    @Column(name = "is_deleted", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isDeleted = false;
}