package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.AchievementCode;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_achievement",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_achievement", columnNames = {"user_id", "achievement_code"}),
        indexes = {
                @Index(name = "idx_user_unlocked", columnList = "user_id, unlocked_at"),
                @Index(name = "idx_user_session", columnList = "user_id, unlock_session_id")
        })
public class UserAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "user_id", columnDefinition = "BIGINT UNSIGNED", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_code", length = 64, nullable = false)
    private AchievementCode achievementCode;

    @Column(name = "progress", nullable = false)
    private Integer progress = 0;

    @Column(name = "target", nullable = false)
    private Integer target = 1;

    @Column(name = "unlocked_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime unlockedAt;

    @Column(name = "unlock_session_id", length = 36)
    private String unlockSessionId;

    public boolean isUnlocked() {
        return unlockedAt != null;
    }
}
