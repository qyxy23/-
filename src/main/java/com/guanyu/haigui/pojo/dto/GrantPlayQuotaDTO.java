package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class GrantPlayQuotaDTO {
    private Long userId;
    private Integer games;
    private String note;
}
