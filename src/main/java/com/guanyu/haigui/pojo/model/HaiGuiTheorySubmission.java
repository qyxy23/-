package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.TheorySubmissionStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "haigui_theory_submission",
        indexes = {
                @Index(name = "idx_session_created", columnList = "game_session_id,created_at"),
                @Index(name = "idx_user_id", columnList = "user_id")
        })
public class HaiGuiTheorySubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "game_session_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String gameSessionId;

    @Column(name = "user_id", columnDefinition = "BIGINT UNSIGNED", nullable = false)
    private Long userId;

    @Column(name = "theory_text", columnDefinition = "TEXT", nullable = false)
    private String theoryText;

    @Column(name = "coverage_score", columnDefinition = "DECIMAL(5,4)")
    private BigDecimal coverageScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", columnDefinition = "ENUM('LOCKED','REJECTED','PARTIAL','WIN')", nullable = false)
    private TheorySubmissionStatus verdict;

    @Column(name = "formal_attempt", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean formalAttempt = false;

    @Column(name = "question_deducted", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean questionDeducted = false;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
