package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.constant.StatusConstant;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.mapper.AiChatSessionMapper;
import com.guanyu.haigui.pojo.model.AiChatMessage;
import com.guanyu.haigui.pojo.model.AiChatSession;
import com.guanyu.haigui.pojo.vo.ChatRoomListVO;
import com.guanyu.haigui.service.ChatService;
import com.guanyu.haigui.utils.RedisServiceUtil;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class ChatServicesImpl implements ChatService {
    private AiChatSessionMapper aiChatMapper;

    private AIManager aiManager;

    private RedisServiceUtil redisServiceUtil;

    // private final Map<Long, List<ChatMessage>> globalMessageMap = new ConcurrentHashMap<>(); // 缓存会话消息


    public String chatWithAI(Long roomId, String message) {
        // 1. 获取当前用户ID BaseContext
        Long userId = BaseContext.getCurrentId();
        List<ChatMessage> messages = new ArrayList<>(); // 统一用官方ChatMessage缓存

        // 2. 校验会话合法性：未开始且消息不含“开始”，拒绝请求
        if (!redisServiceUtil.selectOnlineRooms(roomId) && !message.contains("开始")) {
            return StatusConstant.NoBeginRequest;
        }

        // 3. 首次会话：创建会话记录 + 系统提示消息
        if (!redisServiceUtil.selectOnlineRooms(roomId)) {
            // 3.1 插入会话记录（关联当前用户）
            AiChatSession newSession = AiChatSession.builder()
                    .sessionId(roomId).userId(userId).createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now()).title("海龟汤游戏"+roomId).isDeleted(0).build();
            aiChatMapper.insertSession(newSession); // 插入会话
            //TODO:之后通过AI总结海龟汤游戏来更新标题

            // 3.2 插入系统消息（官方ChatMessage）
            AiChatMessage systemMsg = AiChatMessage.builder().sessionId(roomId)
                    .sendTime(LocalDateTime.now()).isRead(0).build();
            systemMsg.setRole(ChatMessageRole.SYSTEM); // 系统消息角色
            systemMsg.setContent(StatusConstant.SystemPrompt);
            aiChatMapper.insertMsg(systemMsg); // 插入消息（合并原insertSystemMsg）

            // 3.3 初始化缓存：存系统消息
            messages.add(convertSingleAiMessage(systemMsg));
            // globalMessageMap.put(roomId, messages);
            redisServiceUtil.updateOnlineRooms(roomId); // 标记会话已开始
        } else {
            // 3.4 TODO:非首次会话：从缓存读取历史消息
            // List<AiChatMessage> aiMessages = aiChatMapper.selectChatAIMessage(roomId);
            messages = aiChatMapper.selectOfficialChatAIMessage(roomId);
//             messages = convertToOfficialChatMessages(aiMessages);
        }


        // 4. 插入用户消息（官方ChatMessage）
        AiChatMessage userMsg = AiChatMessage.builder().sessionId(roomId)
                .sendTime(LocalDateTime.now()).isRead(0).build();
        userMsg.setRole(ChatMessageRole.USER); // 用户消息角色
        userMsg.setContent(message);
        aiChatMapper.insertMsg(userMsg); // 插入消息（合并原insertUserMsg）
        messages.add(convertSingleAiMessage(userMsg));

        // 5. 调用AI生成回复
        String answer = aiManager.doChat(messages);

        // 6. 插入AI回复（官方ChatMessage，角色为ASSISTANT）
        AiChatMessage assistantMsg = AiChatMessage.builder().sessionId(roomId)
                .sendTime(LocalDateTime.now()).isRead(0).build();
        assistantMsg.setRole(ChatMessageRole.ASSISTANT); // AI回复角色
        assistantMsg.setContent(answer);
        aiChatMapper.insertMsg(assistantMsg);// 插入消息（合并原insertAIMsg）
        messages.add(convertSingleAiMessage(assistantMsg)); // 更新缓存

        // 7. 处理会话结束：若AI回复“游戏结束”
        if (answer.contains("游戏结束")) {
            // 7.1 清除内存缓存
            // globalMessageMap.remove(roomId);
            // 7.2 逻辑删除会话记录（避免物理删除）
            AiChatSession endSession = AiChatSession.builder().sessionId(roomId).isDeleted(1).build();
            aiChatMapper.updateById(endSession);
        }
        return answer;
    }

    public List<ChatMessage> convertToOfficialChatMessages(List<AiChatMessage> aiMessages) {
        return aiMessages.stream()
                .map(aiMsg -> ChatMessage.builder()
                        .role(aiMsg.getRole()) // 确保AiChatMessage的role与官方一致（如ChatMessageRole.SYSTEM）
                        .content((String) aiMsg.getContent())
                        .build())
                .collect(Collectors.toList());
    }


    /**
     * 辅助方法：转换单条AiChatMessage（可选，用于复用逻辑）
     */
    private ChatMessage convertSingleAiMessage(AiChatMessage aiMsg) {
        return ChatMessage.builder()
                .role(aiMsg.getRole()) // 确保AiChatMessage的role与官方ChatMessageRole一致
                .content((String) aiMsg.getContent())
                .build();
    }



    /**
     * 获取当前用户的AI聊天室列表（基础版）
     * @param userId 当前用户ID
     * @return 聊天室列表
     */
    public List<AiChatSession> getAIChatRoomList(Long userId) {
        return aiChatMapper.selectValidSessionsByUserId(userId);
    }

    /**
     * 获取当前用户的AI聊天室列表（含最后一条消息）
     * @param userId 当前用户ID
     * @return 聊天室列表（带最后一条消息）
     */
    public List<ChatRoomListVO> getAIChatRoomListWithLastMessage(Long userId) {
        return aiChatMapper.selectChatRoomListWithLastMessage(userId);
    }


}
