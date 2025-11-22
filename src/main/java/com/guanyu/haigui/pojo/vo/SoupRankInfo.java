package com.guanyu.haigui.pojo.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 海龟汤排名信息
 */
@Data
@Builder
public class SoupRankInfo {
    private String soupId;
    private int currentRank;
    private double hotnessScore;
    private boolean isInTop10;
}