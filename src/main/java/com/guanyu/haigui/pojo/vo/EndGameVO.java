package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guanyu.haigui.Enum.MessageChatType;
import com.guanyu.haigui.pojo.result.ClueSummaryView;
import com.guanyu.haigui.pojo.result.GameSettlementSnapshot;
import com.guanyu.haigui.pojo.result.SettlementTaskView;
import lombok.Data;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class EndGameVO {
    /** WS 路由用 */
    private String roomId;
    private MessageChatType chatType;

    private String soupTitle;
    private String soupBottom;
    /** 最终得分 0–100（任务完成度） */
    private int finalScore;
    private List<SettlementTaskView> completedTasks;
    private List<SettlementTaskView> uncompletedTasks;
    private List<ClueSummaryView> triggeredClues;
    private List<ClueSummaryView> missedClues;

    public static EndGameVO fromSnapshot(GameSettlementSnapshot snapshot) {
        EndGameVO vo = new EndGameVO();
        vo.setRoomId(snapshot.getRoomId());
        vo.setChatType(MessageChatType.GAME_END);
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
