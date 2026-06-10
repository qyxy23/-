package com.guanyu.haigui.pojo.result;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 本局结算快照（任务完成度计分） */
@Data
public class GameSettlementSnapshot {
    private String roomId;
    private String gameSessionId;
    private String soupTitle;
    private String soupBottom;
    private int finalScore;
    private BigDecimal progressPercent = BigDecimal.ZERO;
    private List<SettlementTaskView> completedTasks = new ArrayList<>();
    private List<SettlementTaskView> uncompletedTasks = new ArrayList<>();
    private List<ClueSummaryView> triggeredClues = new ArrayList<>();
    private List<ClueSummaryView> missedClues = new ArrayList<>();
}
