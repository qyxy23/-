package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.CoverReportReason;
import lombok.Data;

@Data
public class SubmitCoverReportDTO {
    private String soupId;
    private CoverReportReason reasonType;
    private String reasonDetail;
}
