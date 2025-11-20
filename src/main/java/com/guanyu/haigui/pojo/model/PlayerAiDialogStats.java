package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.UUID;

@Data
@Entity
@Table(name = "haigui_player_ai_dialog_stats",
       indexes = {
           @Index(name = "idx_session_id", columnList = "session_id"),
           @Index(name = "idx_user_id", columnList = "user_id")
       },
       uniqueConstraints = @UniqueConstraint(columnNames = "stat_id"))
@EqualsAndHashCode(of = "statId")
public class PlayerAiDialogStats {

    @Id
    @GeneratedValue(generator = "uuid2")
    @Column(name = "stat_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private UUID statId;

    // 关联游戏会话
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", referencedColumnName = "session_id", foreignKey = @ForeignKey(name = "fk_player_ai_dialog_stats_session"))
    private GameSession gameSession;

    // 关联玩家
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", foreignKey = @ForeignKey(name = "fk_player_ai_dialog_stats_user"))
    private UserInfo user;

    @Column(name = "dialog_count", columnDefinition = "INT UNSIGNED", nullable = false)
    private Integer dialogCount = 0;

    @Column(name = "yes_count", columnDefinition = "INT UNSIGNED", nullable = false)
    private Integer yesCount = 0;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Date createdAt;

    @Column(name = "updated_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Date updatedAt;
}