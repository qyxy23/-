package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "play_quota_ledger",
        indexes = @Index(name = "idx_user_time", columnList = "user_id, create_time"))
public class PlayQuotaLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id", columnDefinition = "BIGINT UNSIGNED")
    private Long ledgerId;

    @Column(name = "user_id", columnDefinition = "BIGINT UNSIGNED", nullable = false)
    private Long userId;

    @Column(name = "delta", nullable = false)
    private Integer delta;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    @Column(name = "game_session_id", columnDefinition = "VARCHAR(36)")
    private String gameSessionId;

    @Column(name = "source_ref_id", columnDefinition = "BIGINT UNSIGNED")
    private Long sourceRefId;

    @CreationTimestamp
    @Column(name = "create_time", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private LocalDateTime createTime;
}
