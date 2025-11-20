package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.GameStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "haigui_game_session",
       indexes = {
           @Index(name = "idx_soup_id", columnList = "soup_id"),
           @Index(name = "idx_user_id", columnList = "user_id"),
           @Index(name = "idx_chat_session_id", columnList = "chat_session_id"),
           @Index(name = "idx_status", columnList = "status")
       },
       uniqueConstraints = @UniqueConstraint(columnNames = "session_id"))
@EqualsAndHashCode(of = "sessionId")
public class GameSession {

    @Id
    @GeneratedValue(generator = "uuid2")
    @Column(name = "session_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private UUID sessionId;

    // 关联海龟汤
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soup_id", referencedColumnName = "soup_id", foreignKey = @ForeignKey(name = "fk_game_session_soup"))
    private HaiGuiSoup haiGuiSoup;

    // 发起玩家（关联sys_user）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", foreignKey = @ForeignKey(name = "fk_game_session_user"))
    private UserInfo user;

    // 关联聊天会话（假设AiChatSessions是已存在的实体）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id", referencedColumnName = "session_id", foreignKey = @ForeignKey(name = "fk_game_session_chat"))
    private AiChatSession aiChatSessions;

    @Column(name = "start_time", columnDefinition = "DATETIME(6)", nullable = false)
    private Date startTime;

    @Column(name = "end_time", columnDefinition = "DATETIME(6)")
    private Date endTime;

    @Column(name = "current_progress", columnDefinition = "DECIMAL(5,2)", nullable = false)
    private Double currentProgress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "ENUM('ONGOING','COMPLETED','CANCELED')", nullable = false)
    private GameStatus status;

    @Column(name = "is_deleted", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isDeleted = false;

    // 若需关联PlayerAiDialogStats，可添加一对多映射（非必须）
    @OneToMany(mappedBy = "gameSession", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlayerAiDialogStats> dialogStats;
}

