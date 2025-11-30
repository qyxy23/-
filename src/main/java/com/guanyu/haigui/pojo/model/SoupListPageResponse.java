package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.pojo.vo.SoupListItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 海龟汤列表分页响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoupListPageResponse {
    /**
     * 海龟汤列表数据
     */
    private List<SoupListItem> soupList;

    /**
     * 当前页码
     */
    private int currentPage;

    /**
     * 每页大小
     */
    private int pageSize;

    /**
     * 总记录数
     */
    private long totalRecords;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 是否有下一页
     */
    private boolean hasNext;

    /**
     * 是否有上一页
     */
    private boolean hasPrevious;
}