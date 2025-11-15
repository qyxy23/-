package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.util.List;
@Data
public class AllChatGroupMemberListVO {
    private String groupId;          // 群ID（保留原字段）
    private List<ChatGroupMemberVO> members;  // 当前页成员列表
    private Integer total;              // 总成员数

    // 构造器：方便快速创建分页结果
    public AllChatGroupMemberListVO(String groupId,
                                 List<ChatGroupMemberVO> members,
                                 Integer total) {
        this.groupId = groupId;
        this.members = members;
        this.total = total;
    }
}
