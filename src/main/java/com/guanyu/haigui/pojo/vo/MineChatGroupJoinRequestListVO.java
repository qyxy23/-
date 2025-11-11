package com.guanyu.haigui.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MineChatGroupJoinRequestListVO {
    private Integer totalPages;       // 总页数
    private Integer currentPage;      // 当前页码（前端友好，从1开始）
    private Integer pageSize;         // 每页数量
    private List<GroupPermissionVO> data; // 申请列表数据
}