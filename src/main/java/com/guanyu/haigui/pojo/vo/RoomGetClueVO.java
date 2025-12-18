package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guanyu.haigui.Enum.RoomStatus;
import lombok.Data;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class RoomGetClueVO {
    //房间状态
    private RoomStatus roomStatus;
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

