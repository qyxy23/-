package com.guanyu.haigui.mapper;

import com.guanyu.haigui.pojo.model.AiChatMessage;
import com.guanyu.haigui.pojo.model.AiChatSession;
import com.guanyu.haigui.pojo.vo.AiChatMessageDetailVo;
import com.guanyu.haigui.pojo.vo.ChatRoomListVO;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI聊天会话Mapper（扩展：含最后一条消息）
 */
@Mapper
public interface AiChatSessionMapper {
    /**
     * 根据用户ID查询有效聊天室列表（仅基础信息）
     * @param userId 当前用户ID
     * @return 聊天室列表
     */
    List<AiChatSession> selectValidSessionsByUserId(@Param("userId") Long userId);


    /**
     * 根据用户ID查询有效聊天室列表（含最后一条消息）
     * @param userId 当前用户ID
     * @return 聊天室列表（带最后一条消息）
     */
    List<ChatRoomListVO> selectChatRoomListWithLastMessage(@Param("userId") Long userId);


    void insertSession(AiChatSession newSession);


    void insertGroupSession(AiChatSession newSession);


    void insertMsg(AiChatMessage chatMessage);


    void updateById(AiChatSession endSession);

    List<AiChatMessage> selectChatAIMessage(String roomId);

    List<AiChatMessageDetailVo> selectChatAIMessageDetail(String roomId);


    List<ChatMessage> selectOfficialChatAIMessage(String roomId);

    AiChatSession selectSessionBySessionId(String sessionId);
}