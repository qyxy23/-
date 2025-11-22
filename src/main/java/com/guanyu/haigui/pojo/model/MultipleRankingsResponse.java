package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.pojo.vo.HotSoupItem;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 多重榜单响应
 */
@Data
@Builder
public class MultipleRankingsResponse {
    private List<HotSoupItem> top10;
    private List<HotSoupItem> recentHot;
    private RankingStatistics statistics;
}