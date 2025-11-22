package com.guanyu.haigui.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Date;


/**
 * 热门海龟汤项目
 */
@Data
@Builder
public class HotSoupItem {
    private int rank;
    private String soupId;
    private String title;
    private String surface;
    private Integer playCount;
    private Double hotnessScore;
    private Date createdAt;
}