package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MessageChatType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatGroupJoinRequestListVO {
    private Integer totalPages;       // 总页数
    private Integer currentPage;      // 当前页码
    private Integer pageSize;         // 每页数量
    private List<GroupJoinRequestVO> data; // 申请列表数据
    private MessageChatType chatType;
}