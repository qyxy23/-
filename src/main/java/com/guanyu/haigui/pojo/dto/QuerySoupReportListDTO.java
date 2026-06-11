package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class QuerySoupReportListDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    /** PENDING / DISMISSED / SOUP_TAKEN_DOWN */
    private String status;
}
