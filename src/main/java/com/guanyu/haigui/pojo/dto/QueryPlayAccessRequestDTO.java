package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class QueryPlayAccessRequestDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    /** PENDING / APPROVED / REJECTED，空表示全部 */
    private String status;
}
