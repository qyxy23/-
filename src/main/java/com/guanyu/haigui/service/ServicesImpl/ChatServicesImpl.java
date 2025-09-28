package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.constant.StatusConstant;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.model.ChatRoom;
import com.guanyu.haigui.service.ChatService;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Service
public class ChatServicesImpl implements ChatService {

    @Resource
    private AIManager aiManager;

    final Map<Long, List<ChatMessage>> globalMessageMap = new HashMap<>();

    public String chat(Long roomId,String message) {
        List<ChatMessage> messages = new ArrayList<>();
        //如果是首次调用
        //如果没有开始两字
        if(!globalMessageMap.containsKey(roomId) && !message.contains("开始")){
            return StatusConstant.NoBeginRequest;
        }
        //如果有开始两字
        if(message.contains("开始") && !globalMessageMap.containsKey(roomId)){
            final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(StatusConstant.SystemPrompt).build();
            messages.add(systemMessage);
            globalMessageMap.put(roomId,messages);
        }else{
            //如果不是首次调用
            messages = globalMessageMap.get(roomId);
        }
        //AI回复的信息
        final ChatMessage assistantMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(message).build();
        messages.add(assistantMessage);
        //调用AI
        String answer = aiManager.doChat(messages);
        messages.add(ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content(answer).build());

        if(answer.contains("游戏结束")){
            globalMessageMap.remove(roomId);
        }
        //返回结果
        return answer;
    }

    @Override
    public List<ChatRoom> getChatRoomList() {
        List<ChatRoom> chatRoomList = new ArrayList<>();
        for(Map.Entry<Long, List<ChatMessage>> entry : globalMessageMap.entrySet()) {
            ChatRoom chatRoom = new ChatRoom();
            chatRoom.setRoomId(entry.getKey());
            chatRoom.setRoomName("聊天室" + entry.getKey());
            chatRoom.setChatMessageList(entry.getValue());
            chatRoomList.add(chatRoom);
        }
        return chatRoomList;
    }
}
