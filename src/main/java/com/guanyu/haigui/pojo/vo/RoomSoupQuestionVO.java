package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MessageChatType;
import lombok.Data;

@Data
public class RoomSoupQuestionVO {
    //房间id
    private String roomId;

    //问题id
    private String questionId;

    //问题
    private String question;

    //报错信息
    private String message;

    //答案
    private String answer;

    //当前进度
    private Double currentProgress;

    //剩余次数
    private int remainingQuestions;

    //状态
    private String status;

    //消息类型
    private MessageChatType type;


    public static RoomSoupQuestionVO success(String roomId, Long questionId,String question,String answer,Double currentProgress,int remainingQuestions){
        RoomSoupQuestionVO roomSoupQuestionVO = new RoomSoupQuestionVO();
        roomSoupQuestionVO.setRoomId(roomId);
        roomSoupQuestionVO.setQuestionId(questionId.toString());
        roomSoupQuestionVO.setQuestion(question);
        roomSoupQuestionVO.setAnswer(answer);
        roomSoupQuestionVO.setStatus("success");
        roomSoupQuestionVO.setCurrentProgress(currentProgress);
        roomSoupQuestionVO.setRemainingQuestions(remainingQuestions);
        roomSoupQuestionVO.setType(MessageChatType.SOUP_QUESTION);
        return roomSoupQuestionVO;
    }

    public static RoomSoupQuestionVO error(String message){
        RoomSoupQuestionVO roomSoupQuestionVO = new RoomSoupQuestionVO();
        roomSoupQuestionVO.setMessage(message);
        roomSoupQuestionVO.setStatus("error");
        roomSoupQuestionVO.setType(MessageChatType.SOUP_QUESTION);
        return roomSoupQuestionVO;
    }
}
