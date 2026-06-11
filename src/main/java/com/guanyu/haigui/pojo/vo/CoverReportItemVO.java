package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.CoverReportReason;
import com.guanyu.haigui.Enum.CoverReportStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CoverReportItemVO {
    private Long reportId;
    private String soupId;
    private String soupTitle;
    private String coverUrl;
    private Long reporterId;
    private String reporterName;
    private CoverReportReason reasonType;
    private String reasonTypeLabel;
    private String reasonDetail;
    private CoverReportStatus status;
    private String statusLabel;
    private String handleNote;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
