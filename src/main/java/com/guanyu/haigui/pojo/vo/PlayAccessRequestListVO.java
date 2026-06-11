package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class PlayAccessRequestListVO {
    private List<PlayAccessRequestVO> list;
    private long total;
    private int pages;
    private int pageNum;
}
