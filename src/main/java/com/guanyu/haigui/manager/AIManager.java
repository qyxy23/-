package com.guanyu.haigui.manager;

import cn.hutool.core.collection.CollUtil;
import com.volcengine.ark.runtime.model.completion.chat.*;
import com.volcengine.ark.runtime.service.ArkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/*
AI调用
 */
@Service("aiManager")
public class AIManager {
    @Autowired(required = false)
    @Nullable
    private ArkService arkService;

    // 只允许用户传入一个系统上下文和用户输入
    public String doChat(String systemPrompt, String userPrompt) {
        // return "ai已生成";
        // 检查AI服务是否可用
        if (arkService == null) {
            return "AI服务未配置或不可用";
        }
        System.out.println("开始调用AI");

        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt)
                .build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        messages.add(systemMessage);
        messages.add(userMessage);
        return doChat(messages);
    }

    // 允许用户传入任意条消息队列
    public String doChat(List<ChatMessage> chatMessageList) {
        // 检查AI服务是否可用
        if (arkService == null) {
            return "AI服务未配置或不可用";
        }

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                // 指定您创建的方舟推理接入点 ID，此处已帮您修改为您的推理接入点 ID
                .model("ep-20250913203623-2bv46")
                .messages(chatMessageList)
                .build();


        try {
            // 1. 调用同步API获取完整响应（替换原流式调用）
            ChatCompletionResult result = arkService.createChatCompletion(
                    chatCompletionRequest
            );

            // 2. 提取响应中的choices列表（匹配你原有的逻辑）
            List<ChatCompletionChoice> choices = result.getChoices();

            // 3. 判断choices是否为空（原逻辑保留）
            if (CollUtil.isEmpty(choices)) {
                return "对AI请求失败";
            }

            // 4. 提取第一个choice的内容（原逻辑保留）
            String content = (String) choices.get(0).getMessage().getContent();
            System.out.println("AI返回内容 " + content);
            return content;
        } catch (Exception e) {
            // 5. 异常处理（可选，增强鲁棒性）
            e.printStackTrace();
            return "AI请求失败：" + e.getMessage();
        }
    }
}