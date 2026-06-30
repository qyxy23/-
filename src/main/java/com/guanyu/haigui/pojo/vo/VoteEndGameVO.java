package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guanyu.haigui.Enum.MessageChatType;
import com.guanyu.haigui.Enum.VoteType;
import lombok.Data;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class VoteEndGameVO {
    //当前已投人数
    private Integer totalVoters;
    //截止时间
    private LocalDateTime endTime;
    //当前同意人数
    private int agreeNum;
    //发起投票成功与否
    private String status;
    //错误信息
    private String msg;
    //消息类型
    private MessageChatType chatType;
    /** END_GAME | THEORY_SUBMIT */
    private VoteType voteType;
    /** 推理投票时的草案预览（截断） */
    private String theoryPreview;
    private Integer draftVersion;

    public static VoteEndGameVO error(String s) {
        VoteEndGameVO voteEndGameVO = new VoteEndGameVO();
        voteEndGameVO.setStatus("error");
        voteEndGameVO.setMsg(s);
        return voteEndGameVO;
    }

    public static VoteEndGameVO success(int totalVoters, LocalDateTime countDown, int agreeNum) {
        VoteEndGameVO voteEndGameVO = new VoteEndGameVO();
        voteEndGameVO.setStatus("success");
        voteEndGameVO.setTotalVoters(totalVoters);
        voteEndGameVO.setEndTime(countDown);
        voteEndGameVO.setAgreeNum(agreeNum);
        return voteEndGameVO;
    }
}
