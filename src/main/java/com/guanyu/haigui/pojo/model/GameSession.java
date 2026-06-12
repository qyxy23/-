package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.GameEndReason;
import com.guanyu.haigui.Enum.PlayMode;
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
                @Index(name = "idx_play_mode", columnList = "play_mode"),
                @Index(name = "idx_room_id", columnList = "room_id"),
                @Index(name = "idx_status", columnList = "status")
        })
@EqualsAndHashCode(of = "sessionId")
public class GameSession {

    @Id
    @Column(name = "session_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String sessionId;

    @Column(name = "soup_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String soupId;

    @Column(name = "user_id", columnDefinition = "BIGINT UNSIGNED", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "play_mode", columnDefinition = "ENUM('MULTI','SOLO')", nullable = false)
    private PlayMode playMode = PlayMode.SOLO;

    @Column(name = "room_id", columnDefinition = "VARCHAR(36)")
    private String roomId;

    @Column(name = "chat_session_id", columnDefinition = "VARCHAR(36)")
    private String chatSessionId;

    // 新增字段：剩余问答次数
    @Column(name = "remaining_questions", columnDefinition = "INT", nullable = false)
    private Integer remainingQuestions = 30;

    @Column(name = "start_time", columnDefinition = "DATETIME(6)", nullable = false)
    @CreationTimestamp
    private LocalDateTime startTime;

    @Column(name = "end_time", columnDefinition = "DATETIME(6)")
    private LocalDateTime endTime;

    // 修改为 BigDecimal 以匹配数据库 DECIMAL(5,2)
    @Column(name = "current_progress", columnDefinition = "DECIMAL(5,2)", nullable = false)
    private BigDecimal currentProgress = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "ENUM('ONGOING','COMPLETED','CANCELED')", nullable = false)
    private GameSessionStatus status = GameSessionStatus.ONGOING;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", columnDefinition = "ENUM('QUESTIONS_EXHAUSTED','MANUAL_GIVE_UP','VOTE_PASSED','ROOM_DISBANDED','GUESS_CORRECT')")
    private GameEndReason endReason;

    @Column(name = "is_deleted", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "score", columnDefinition = "DECIMAL(5,2)")
    private BigDecimal score = BigDecimal.ZERO;

    @Column(name = "quota_charged", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean quotaCharged = false;

    // 关联的海龟汤实体
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soup_id", referencedColumnName = "soup_id",
            foreignKey = @ForeignKey(name = "fk_game_session_soup"),
            insertable = false, updatable = false)
    private HaiGuiSoup soup;

    // 关联的用户实体
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id",
            foreignKey = @ForeignKey(name = "fk_game_session_user"),
            insertable = false, updatable = false)
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
    }

    /**
     * 增加进度
     */
    public void addProgress(BigDecimal increment) {
        this.currentProgress = this.currentProgress.add(increment);
    }

    /**
     * 减少剩余问答次数
     * @return 是否达到最大问答次数
     */
    public boolean decrementRemainingQuestions() {
        if (this.remainingQuestions > 0) {
            this.remainingQuestions--;
            return this.remainingQuestions == 0;
        }
        return true;
    }



    /**
     * 获取游戏持续时间（分钟）
     */
    public Long getDurationMinutes() {
        LocalDateTime end = this.endTime != null ? this.endTime : LocalDateTime.now();
        return java.time.Duration.between(this.startTime, end).toMinutes();
    }
}