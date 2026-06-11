package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.CoverReportReason;
import com.guanyu.haigui.Enum.SoupContentReportStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SoupContentReportItemVO {
    private Long reportId;
    private String soupId;
    private String soupTitle;
    private String soupSurface;
    private Long reporterId;
    private String reporterName;
    private CoverReportReason reasonType;
    private String reasonTypeLabel;
    private String reasonDetail;
    private SoupContentReportStatus status;
    private String statusLabel;
    private String handleNote;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
