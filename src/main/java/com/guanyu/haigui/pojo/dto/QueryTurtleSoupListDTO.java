package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class QueryTurtleSoupListDTO {
    private Integer pageNum = 1; // 默认第一页
    private Integer pageSize = 10; // 默认每页10条
    private String soupTitle; // 可选：按标题搜索
    private String auditStatus; // 可选：按审核状态筛选
}