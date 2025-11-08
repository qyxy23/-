package com.guanyu.haigui.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatGroupMemberListVO {
    private String groupId;          // 群ID（保留原字段）
    private List<ChatGroupMemberVO> members;  // 当前页成员列表
    private Long total;              // 总成员数
    private Integer pageNum;         // 当前页码（前端展示用，从1开始）
    private Integer pageSize;        // 每页大小
    private Integer totalPages;      // 总页数

    // 构造器：方便快速创建分页结果
    public ChatGroupMemberListVO(String groupId,
                                 List<ChatGroupMemberVO> members,
                                 Long total,
                                 Integer pageNum,
                                 Integer pageSize) {
        this.groupId = groupId;
        this.members = members;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        // 计算总页数（向上取整）
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }
}