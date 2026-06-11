package com.guanyu.haigui.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CoverReportListVO {
    private List<CoverReportItemVO> list;
    private long total;
    private int pages;
    private int pageNum;
    private int pageSize;
}
