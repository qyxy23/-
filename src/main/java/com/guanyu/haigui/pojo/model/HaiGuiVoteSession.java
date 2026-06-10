package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "hai_gui_vote_session",
        indexes = {
                @Index(name = "idx_session_id", columnList = "session_id"),
                @Index(name = "idx_initiator_id", columnList = "initiator_id"),
                @Index(name = "idx_status", columnList = "status"),
                @Index(name = "idx_created_at", columnList = "created_at"),
                @Index(name = "idx_total_voters", columnList = "total_voters"),
                @Index(name = "idx_agreed_votes", columnList = "agreed_votes")
        })
@Data
public class HaiGuiVoteSession {

    @Id
    @Column(name = "vote_session_id", length = 36, nullable = false)
    private String voteSessionId;

    /** 关联的游戏会话 ID（haigui_game_session.session_id） */
    @Column(name = "session_id", length = 36, nullable = false)
    private String gameSessionId;

    @Column(name = "initiator_id", nullable = false)
    private Long initiatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "ENUM('ONGOING','PASSED','FAILED','CANCELLED')", nullable = false)
    private VoteStatus status = VoteStatus.ONGOING;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "total_voters", nullable = false)
    private Integer totalVoters = 0;

    @Column(name = "agreed_votes", nullable = false)
    private Integer agreedVotes = 0;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", referencedColumnName = "session_id", insertable = false, updatable = false)
    private GameSession gameSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_id", referencedColumnName = "user_id", insertable = false, updatable = false)
    private UserInfo sysUser;

    public enum VoteStatus {
        ONGOING,
        PASSED,
        FAILED,
        CANCELLED
    }

    public void incrementAgreedVotes() {
        this.agreedVotes++;
    }

    public void decrementAgreedVotes() {
        if (this.agreedVotes > 0) {
            this.agreedVotes--;
        }
    }
}
