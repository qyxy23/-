package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guanyu.haigui.pojo.result.ClueSummaryView;
import com.guanyu.haigui.pojo.result.GameHistoryMemberView;
import com.guanyu.haigui.pojo.result.GameHistoryQuestionView;
import com.guanyu.haigui.pojo.result.GameHistoryTimelineItem;
import com.guanyu.haigui.pojo.result.GameSettlementSnapshot;
import com.guanyu.haigui.pojo.result.SettlementTaskView;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class getAIChatListDetailVO {
    private String roomId;
    private String gameSessionId;
    /** MULTI | SOLO */
    private String playMode;
    private String soupTitle;
    private String soupSurface;
    private String soupBottom;
    private int finalScore;
    private LocalDateTime endTime;
    private List<SettlementTaskView> completedTasks;
    private List<SettlementTaskView> uncompletedTasks;
    private List<ClueSummaryView> triggeredClues;
    private List<ClueSummaryView> missedClues;
    /** 成员贡献分与 MVP */
    private List<GameHistoryMemberView> members;
    private Long mvpUserId;
    /** 本局 AI 问答（按时间升序） */
    private List<GameHistoryQuestionView> questions;
    /** 大厅聊天 + AI 提问统一时间线 */
    private List<GameHistoryTimelineItem> timeline;
    /** 结束原因枚举名，如 GUESS_CORRECT（Redis 复盘缓存字段） */
    private String endReason;

    public static getAIChatListDetailVO fromSnapshot(GameSettlementSnapshot snapshot) {
        getAIChatListDetailVO vo = new getAIChatListDetailVO();
        vo.setRoomId(snapshot.getRoomId());
        vo.setGameSessionId(snapshot.getGameSessionId());
        vo.setSoupTitle(snapshot.getSoupTitle());
        vo.setSoupBottom(snapshot.getSoupBottom());
        vo.setFinalScore(snapshot.getFinalScore());
        vo.setCompletedTasks(snapshot.getCompletedTasks());
        vo.setUncompletedTasks(snapshot.getUncompletedTasks());
        vo.setTriggeredClues(snapshot.getTriggeredClues());
        vo.setMissedClues(snapshot.getMissedClues());
        return vo;
    }
}
