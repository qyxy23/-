package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class HandleCoverReportDTO {
    private Long reportId;
    /** DISMISS 驳回举报；REMOVE_COVER 下架封面 */
    private String action;
    private String handleNote;
}
