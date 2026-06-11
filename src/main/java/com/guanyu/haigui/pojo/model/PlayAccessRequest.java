package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.PlayAccessRequestStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "play_access_request",
        indexes = {
                @Index(name = "idx_user_status", columnList = "user_id, status"),
                @Index(name = "idx_status_time", columnList = "status, create_time")
        })
public class PlayAccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id", columnDefinition = "BIGINT UNSIGNED")
    private Long requestId;

    @Column(name = "user_id", columnDefinition = "BIGINT UNSIGNED", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "ENUM('PENDING','APPROVED','REJECTED')", nullable = false)
    private PlayAccessRequestStatus status = PlayAccessRequestStatus.PENDING;

    @Column(name = "user_message", columnDefinition = "TEXT")
    private String userMessage;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "reviewer_id", columnDefinition = "BIGINT UNSIGNED")
    private Long reviewerId;

    @Column(name = "granted_games")
    private Integer grantedGames;

    @Column(name = "reviewed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "create_time", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", columnDefinition = "DATETIME(6)", nullable = false)
    private LocalDateTime updateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", insertable = false, updatable = false)
    private UserInfo user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", referencedColumnName = "user_id", insertable = false, updatable = false)
    private UserInfo reviewer;
}
