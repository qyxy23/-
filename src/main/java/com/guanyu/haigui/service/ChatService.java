package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.vo.*;

import java.util.List;

public interface ChatService {


    /**
     * 聊天
     * @param message 问题
     * @return
     */
    FirstChatVo doFirstChatWithAi(String message);

    /**
     * 聊天
     * @param roomId 房间id
     * @param message 问题
     * @return
     */
    String chatWithAI(String roomId,String message);


    List<ChatListVO> getAIChatList(Long currentId);

    getAIChatListDetailVO getAIChatListDetail(String roomId, String gameSessionId);

    // ChatRoomListDetailVO getAIChatRoomListDetail(String sessionId);
}
