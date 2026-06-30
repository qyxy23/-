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
    /** WS 路由用（多人）；单人用 gameSessionId */
    private String roomId;
    private String gameSessionId;
    private MessageChatType chatType;

    private String soupTitle;
    private String soupBottom;
    /** 最终得分 0–100（任务完成度） */
    private int finalScore;
    private List<SettlementTaskView> completedTasks;
    private List<SettlementTaskView> uncompletedTasks;
    private List<ClueSummaryView> triggeredClues;
    private List<ClueSummaryView> missedClues;
    /** 完整复盘（对局结束时预计算，Redis 缓存 3 天） */
    private getAIChatListDetailVO replayDetail;
    /** 本局新解锁成就（当前用户） */
    private List<AchievementView> newlyUnlockedAchievements;
    /** 结束原因枚举名，如 GUESS_CORRECT */
    private String endReason;

    public static EndGameVO fromSnapshot(GameSettlementSnapshot snapshot) {
        EndGameVO vo = new EndGameVO();
        vo.setRoomId(snapshot.getRoomId());
        vo.setGameSessionId(snapshot.getGameSessionId());
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
