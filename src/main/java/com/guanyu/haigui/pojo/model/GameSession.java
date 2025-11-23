package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 海龟汤游戏会话数据模型（映射haigui_game_session表）
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
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
    @Column(name = "session_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String sessionId;

    @Column(name = "soup_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String soupId;

    @Column(name = "user_id", columnDefinition = "BIGINT UNSIGNED", nullable = false)
    private Long userId;

    @Column(name = "chat_session_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String chatSessionId;

    @Column(name = "start_time", columnDefinition = "DATETIME(6)", nullable = false)
    @CreationTimestamp
    private LocalDateTime startTime;

    @Column(name = "end_time", columnDefinition = "DATETIME(6)")
    private LocalDateTime endTime;

    @Column(name = "current_progress", columnDefinition = "DECIMAL(5,2)", nullable = false)
    private BigDecimal currentProgress = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "ENUM('ONGOING','COMPLETED','CANCELED')", nullable = false)
    private GameSessionStatus status = GameSessionStatus.ONGOING;

    @Column(name = "is_deleted", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "score", columnDefinition = "DECIMAL(5,2)")
    private BigDecimal score = BigDecimal.ZERO;

    // 关联的海龟汤实体
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soup_id", referencedColumnName = "soup_id", foreignKey = @ForeignKey(name = "fk_game_session_soup"), insertable = false, updatable = false)
    private HaiGuiSoup soup;

    // 关联的用户实体
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", foreignKey = @ForeignKey(name = "fk_game_session_user"), insertable = false, updatable = false)
    private UserInfo user;

    /**
     * 构造函数
     */
    public GameSession(String sessionId, String soupId, Long userId, String chatSessionId) {
        this.sessionId = sessionId;
        this.soupId = soupId;
        this.userId = userId;
        this.chatSessionId = chatSessionId;
    }

    /**
     * 便利构造函数（自动生成sessionId）
     */
    public GameSession(String soupId, Long userId, String chatSessionId) {
        this(UUID.randomUUID().toString(), soupId, userId, chatSessionId);
    }

    /**
     * 游戏会话状态枚举
     */
    public enum GameSessionStatus {
        ONGOING,    // 进行中
        COMPLETED,  // 已完成
        CANCELED    // 已取消
    }

    /**
     * 检查游戏是否正在进行
     */
    public boolean isOngoing() {
        return GameSessionStatus.ONGOING.equals(this.status);
    }

    /**
     * 检查游戏是否已完成
     */
    public boolean isCompleted() {
        return GameSessionStatus.COMPLETED.equals(this.status);
    }

    /**
     * 检查游戏是否已取消
     */
    public boolean isCanceled() {
        return GameSessionStatus.CANCELED.equals(this.status);
    }

    /**
     * 完成游戏
     */
    public void complete() {
        this.status = GameSessionStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
    }

    /**
     * 取消游戏
     */
    public void cancel() {
        this.status = GameSessionStatus.CANCELED;
        this.endTime = LocalDateTime.now();
    }

    /**
     * 更新进度
     */
    public void updateProgress(BigDecimal progress) {
        this.currentProgress = progress;
        if (this.currentProgress.compareTo(BigDecimal.valueOf(100)) >= 0) {
            this.currentProgress = BigDecimal.valueOf(100);
            complete();
        }
    }

    /**
     * 增加进度
     */
    public void addProgress(BigDecimal increment) {
        BigDecimal newProgress = this.currentProgress.add(increment);
        updateProgress(newProgress);
    }

    /**
     * 获取游戏持续时间（分钟）
     */
    public Long getDurationMinutes() {
        LocalDateTime end = this.endTime != null ? this.endTime : LocalDateTime.now();
        return java.time.Duration.between(this.startTime, end).toMinutes();
    }
}

