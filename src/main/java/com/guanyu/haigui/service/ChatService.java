package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.vo.ChatRoomListVO;

import java.util.List;

public interface ChatService {

    /**
     * 聊天
     * @param roomId 房间id
     * @param question 问题
     * @return
     */
    String chatWithAI(Long roomId,String question);


    /**
     * 获取当前用户的聊天室列表（含最后一条消息）
     * @param userId 当前用户ID
     * @return 聊天室列表（带最后一条消息）
     */
    List<ChatRoomListVO> getAIChatRoomListWithLastMessage(Long userId);
}
