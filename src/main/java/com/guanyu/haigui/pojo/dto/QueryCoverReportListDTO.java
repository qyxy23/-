package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.CoverReportStatus;
import lombok.Data;

@Data
public class QueryCoverReportListDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private CoverReportStatus status;
}
