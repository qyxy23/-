package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guanyu.haigui.Enum.VoteType;
import com.guanyu.haigui.pojo.result.ClueSummaryView;
import com.guanyu.haigui.pojo.result.SettlementTaskView;
import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.vo.MemberContributionView;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class RoomGetClueVO {
    //房间状态
    private RoomStatus roomStatus;
    /** 游戏会话 ID（进行中时有值） */
    private String gameSessionId;
    //汤面
    private String soupSurface;
    //汤底
    private String soupBottom;
    //当前进度
    private Double progress;
    //剩余问答次数
    private Integer remainingQuestions;
    //问题
    private List<QuestionClass> question;
    //错误信息
    private String message;
    //投票总人数
    private Integer totalVoters;
    //同意人数
    private Integer agreedVotes;
    //自己是否投过票
    private Boolean hasVoted;
    //结束时间
    private LocalDateTime endTime;
    //是否同意
    private Boolean agreed;

    /** 对局内已触发线索卡片（P2） */
    private List<ClueSummaryView> triggeredClues;

    /** 已完成推理任务摘要（P2） */
    private List<SettlementTaskView> completedTasks;

    /** 提交推理解锁状态（P1） */
    private TheoryUnlockVO theoryUnlock;

    /** 成员实时贡献（P3，仅多人进行中） */
    private List<MemberContributionView> memberContributions;

    /** 多人推理草案与编辑锁 */
    private TheoryDraftVO theoryDraft;

    /** 进行中投票类型（VOTING 时有值） */
    private VoteType voteType;

    /** 推理投票中的草案预览 */
    private String theoryVotePreview;

    /** 推理投票绑定的草案版本 */
    private Integer theoryVoteDraftVersion;


    @Data
    public static class QuestionClass{
        private String question;
        private String answer;
        private String sendTime;
    }

    public static RoomGetClueVO error(String message, RoomStatus roomStatus){
        RoomGetClueVO vo = new RoomGetClueVO();
        vo.setRoomStatus(roomStatus);
        vo.setMessage(message);
        return vo;
    }
}

