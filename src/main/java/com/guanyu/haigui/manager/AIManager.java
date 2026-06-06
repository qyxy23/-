package com.guanyu.haigui.manager;

import cn.hutool.core.collection.CollUtil;
import com.guanyu.haigui.pojo.model.AiInferenceEndpoint;
import com.guanyu.haigui.repository.AiInferenceEndpointRepository;
import com.guanyu.haigui.utils.RedisServiceUtil;
import com.volcengine.ark.runtime.model.completion.chat.*;
import com.volcengine.ark.runtime.service.ArkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
AI调用
 */
@Service("aiManager")
@Slf4j
@RequiredArgsConstructor
public class AIManager {
    private final ArkService arkService;
    private final RedisServiceUtil redisServiceUtil;
    private final AiInferenceEndpointRepository modelRepository;


    // 只允许用户传入一个系统上下文和用户输入
    public String doChat(String systemPrompt, String userPrompt) {
        if (arkService == null) {
            return "AI服务未配置或不可用";
        }
        System.out.println("开始调用AI" + systemPrompt+ "\n" + userPrompt);

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

        String model = resolveActiveModelEndpointId();
        if (model == null || model.isEmpty()) {
            log.error("未找到可用的 AI 接入点配置");
            return "系统错误：未配置可用的AI模型，请联系管理员切换接入点";
        }

        log.debug("使用模型: {}", model);
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(model)
                .messages(chatMessageList)
                .build();

        try {
            // 调用同步API获取完整响应
            ChatCompletionResult result = arkService.createChatCompletion(chatCompletionRequest);
            List<ChatCompletionChoice> choices = result.getChoices();

            if (CollUtil.isEmpty(choices)) {
                log.warn("AI返回空响应");
                return "对AI请求失败";
            }

            String content = (String) choices.get(0).getMessage().getContent();
            log.info("AI返回内容: {}", content);
            return content;
        } catch (Exception e) {
            log.error("AI请求失败：{}", e.getMessage(), e);
            return "AI请求失败：" + e.getMessage();
        }
    }

    /**
     * 解析当前应使用的接入点：Redis 缓存需在库中仍为启用状态，否则回退到首个启用项。
     */
    private String resolveActiveModelEndpointId() {
        String cached = redisServiceUtil.selectChatModel();
        if (cached != null && !cached.isEmpty()) {
            Optional<AiInferenceEndpoint> cachedModel = modelRepository.findByEndpointId(cached);
            if (cachedModel.isPresent() && Boolean.TRUE.equals(cachedModel.get().getIsActive())) {
                return cached;
            }
            log.warn("Redis 中的模型无效或已禁用: {}", cached);
        }

        String model = getActiveModelFromDatabase();
        if (model != null && !model.isEmpty()) {
            try {
                redisServiceUtil.updateChatModel(model);
                log.info("已将模型 {} 缓存到 Redis", model);
            } catch (Exception e) {
                log.warn("模型缓存到 Redis 失败: {}", e.getMessage());
            }
        }
        return model;
    }

    /**
     * 从数据库获取启用的模型
     * @return 模型ID（endpointId），如果没有启用的模型则返回null
     */
    private String getActiveModelFromDatabase() {
        try {
            // 查询所有启用的模型，按ID排序取第一个
            List<AiInferenceEndpoint> activeModels = modelRepository.findByIsActiveTrueOrderByIdAsc();

            if (CollUtil.isEmpty(activeModels)) {
                log.warn("数据库中没有启用的模型配置");
                return null;
            }

            // 取第一个启用的模型
            AiInferenceEndpoint activeModel = activeModels.get(0);
            log.info("从数据库获取到启用的模型: ID={}, Name={}, EndpointId={}",
                    activeModel.getId(), activeModel.getModelName(), activeModel.getEndpointId());

            return activeModel.getEndpointId();
        } catch (Exception e) {
            log.error("查询数据库模型配置失败: {}", e.getMessage(), e);
            return null;
        }
    }
}