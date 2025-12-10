package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "hai_gui_vote_record",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"vote_session_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_vote_session_id", columnList = "vote_session_id"),
                @Index(name = "idx_user_id", columnList = "user_id"),
                @Index(name = "idx_vote_time", columnList = "created_at"),
                @Index(name = "idx_vote_option", columnList = "vote_option")
        })
@Data
public class HaiGuiVoteRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vote_id", columnDefinition = "BIGINT UNSIGNED")
    private Long voteId;

    @Column(name = "vote_session_id", length = 36, nullable = false)
    private String voteSessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_option", columnDefinition = "ENUM('AGREE','DISAGREE','ABSTAIN')", nullable = false)
    private VoteOption voteOption;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 关联关系
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_session_id", referencedColumnName = "vote_session_id",
            insertable = false, updatable = false)
    private HaiGuiVoteSession voteSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id",
            insertable = false, updatable = false)
    private UserInfo sysUser;

    // 获取投票时间（等同于创建时间）
    @Transient
    public LocalDateTime getVoteTime() {
        return createdAt;
    }

    // 枚举定义
    public enum VoteOption {
        AGREE,      // 同意
        DISAGREE,   // 不同意
        ABSTAIN     // 弃权
    }
}