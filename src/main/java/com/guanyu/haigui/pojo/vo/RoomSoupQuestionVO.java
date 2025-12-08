package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MessageChatType;
import lombok.Data;

@Data
public class RoomSoupQuestionVO {
    //房间id
    private String roomId;

    //问题
    private String question;

    //报错信息
    private String message;

    //答案
    private String answer;

    //消息类型
    private MessageChatType type;


    public static RoomSoupQuestionVO success(String roomId, String question,String answer){
        RoomSoupQuestionVO roomSoupQuestionVO = new RoomSoupQuestionVO();
        roomSoupQuestionVO.setRoomId(roomId);
        roomSoupQuestionVO.setQuestion(question);
        roomSoupQuestionVO.setAnswer(answer);
        roomSoupQuestionVO.setMessage("success");
        roomSoupQuestionVO.setType(MessageChatType.SOUP_QUESTION);
        return roomSoupQuestionVO;
    }

    public static RoomSoupQuestionVO error(String message){
        RoomSoupQuestionVO roomSoupQuestionVO = new RoomSoupQuestionVO();
        roomSoupQuestionVO.setMessage(message);
        roomSoupQuestionVO.setType(MessageChatType.SOUP_QUESTION);
        return roomSoupQuestionVO;
    }
}
