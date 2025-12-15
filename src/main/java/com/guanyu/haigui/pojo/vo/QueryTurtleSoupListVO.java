package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class QueryTurtleSoupListVO {
    private Integer total; // 总记录数
    private Integer pages; // 总页数
    private List<TurtleSoupItem> list; // 当前页数据

    @Data
    public static class TurtleSoupItem {
        private Long auditId;// 审核ID
        private String soupTitle; // 标题
        private String soupSurface; // 汤面
        private String auditStatus; // 审核状态
        private String createTime; // 创建时间
    }
}