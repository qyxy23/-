package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.model.ChatRoom;

import java.util.List;

public interface ChatService {

    /**
     * 聊天
     * @param roomId 房间id
     * @param question 问题
     * @return
     */
    String chat(Long roomId,String question);


    List<ChatRoom> getChatRoomList();
}
