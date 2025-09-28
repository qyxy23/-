package com.guanyu.haigui.manager;

import cn.hutool.core.collection.CollUtil;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChoice;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/*
AI调用
 */
@Service
public class AIManager {
    @Resource
    private ArkService arkService;

    //只允许用户传入一个系统上下文和用户输入
    public String doChat(String systemPrompt,String userPrompt) {
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        messages.add(systemMessage);
        messages.add(userMessage);
        return doChat(messages);
    }

    //允许用户传入任意条消息队列
    public String doChat(List<ChatMessage> chatMessageList) {
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                // 指定您创建的方舟推理接入点 ID，此处已帮您修改为您的推理接入点 ID
                .model("ep-20250913203623-2bv46")
                .messages(chatMessageList)
                .build();
        List<ChatCompletionChoice> choices = arkService.createChatCompletion(chatCompletionRequest).getChoices();
        if (CollUtil.isEmpty(choices)) {
            return "对AI请求失败";
        }
        String content = (String) choices.get(0).getMessage().getContent();
        System.out.println("AI返回内容 " + content);
        return content;
    }
}
