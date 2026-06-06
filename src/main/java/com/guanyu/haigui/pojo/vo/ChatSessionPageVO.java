package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class ChatSessionPageVO {
    private List<ChatSessionVO> list;
    /** keyset 游标，无更多时为 null */
    private String nextCursor;
    private Boolean hasMore;
}
