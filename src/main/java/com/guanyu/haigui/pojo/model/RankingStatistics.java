package com.guanyu.haigui.pojo.model;

import lombok.Builder;
import lombok.Data;

/**
 * 榜单统计信息
 */
@Builder
@Data
public class RankingStatistics {
    private int totalRankedSoups;
    private double top10TotalHotness;
    private double totalHotness;
    private double top10Percentage;
}