package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.CoverReportReason;
import com.guanyu.haigui.Enum.CoverReportStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "soup_cover_report",
        indexes = {
                @Index(name = "idx_soup_status", columnList = "soup_id, status"),
                @Index(name = "idx_status_created", columnList = "status, created_at"),
                @Index(name = "idx_reporter_pending", columnList = "reporter_id, soup_id, status")
        })
public class SoupCoverReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id", columnDefinition = "BIGINT UNSIGNED")
    private Long reportId;

    @Column(name = "soup_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String soupId;

    @Column(name = "reporter_id", columnDefinition = "BIGINT UNSIGNED", nullable = false)
    private Long reporterId;

    @Column(name = "cover_url", columnDefinition = "VARCHAR(512)", nullable = false)
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", columnDefinition = "VARCHAR(32)", nullable = false)
    private CoverReportReason reasonType;

    @Column(name = "reason_detail", columnDefinition = "VARCHAR(500)")
    private String reasonDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "VARCHAR(20)", nullable = false)
    private CoverReportStatus status = CoverReportStatus.PENDING;

    @Column(name = "handler_id", columnDefinition = "BIGINT UNSIGNED")
    private Long handlerId;

    @Column(name = "handle_note", columnDefinition = "VARCHAR(500)")
    private String handleNote;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "handled_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime handledAt;
}
