package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class HandleSoupContentReportDTO {
    private Long reportId;
    /** DISMISS 驳回；TAKE_DOWN 下架海龟汤 */
    private String action;
    private String handleNote;
}
