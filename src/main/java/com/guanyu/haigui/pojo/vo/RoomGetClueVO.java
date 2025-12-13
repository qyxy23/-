package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class RoomGetClueVO {
    //问题
    private List<QuestionClass> question;

    @Data
    public static class QuestionClass{
        private String question;
        private String answer;
        private String sendTime;
    }

    // 添加成功构建方法
    public static RoomGetClueVO success(List<QuestionClass> questions) {
        RoomGetClueVO vo = new RoomGetClueVO();
        vo.question = questions;
        return vo;
    }
}

