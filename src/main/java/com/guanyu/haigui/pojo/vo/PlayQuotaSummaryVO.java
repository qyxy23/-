package com.guanyu.haigui.pojo.vo;

import lombok.Data;

@Data
public class PlayQuotaSummaryVO {
    private Integer globalGamesRemaining;
    private Integer ongoingUnchargedCount;
    private Integer effectiveRemaining;
    private Boolean unlimited;
    private Integer totalConsumed;
    private Boolean hasPendingRequest;
    private String latestRequestStatus;
}
