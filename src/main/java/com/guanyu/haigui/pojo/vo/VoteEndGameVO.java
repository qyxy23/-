package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MessageChatType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VoteEndGameVO {
    //当前已投人数
    private int voteNum;
    //截止时间
    private LocalDateTime countDown;
    //当前同意人数
    private int agreeNum;
    //发起投票成功与否
    private String status;
    //错误信息
    private String msg;
    //消息类型
    private MessageChatType type;
    public static VoteEndGameVO error(String s) {
        VoteEndGameVO voteEndGameVO = new VoteEndGameVO();
        voteEndGameVO.setStatus("error");
        voteEndGameVO.setMsg(s);
        voteEndGameVO.setType(MessageChatType.START_VOTING);
        return voteEndGameVO;
    }

    public static VoteEndGameVO success(int voteNum, LocalDateTime countDown, int agreeNum) {
        VoteEndGameVO voteEndGameVO = new VoteEndGameVO();
        voteEndGameVO.setStatus("success");
        voteEndGameVO.setVoteNum(voteNum);
        voteEndGameVO.setCountDown(countDown);
        voteEndGameVO.setAgreeNum(agreeNum);
        voteEndGameVO.setType(MessageChatType.START_VOTING);
        return voteEndGameVO;
    }
}
