package com.guanyu.haigui.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI客户端工具类
 * 用于调用生成式AI服务
 */
@Component
@Slf4j
public class AiClientUtil {

    /**
     * 调用AI生成回答
     *
     * @param prompt 提示词
     * @return AI回答
     */
    public String generateResponse(String prompt) {
        try {
            log.info("开始调用AI服务，提示词长度: {}", prompt.length());

            // TODO: 实现实际的AI服务调用
            // 这里可以集成各种AI服务，如：
            // - OpenAI GPT API
            // - 百度文心一言
            // - 阿里通义千问
            // - 本地部署的模型等

            // 示例实现（模拟AI回答）
            return generateMockResponse(prompt);

        } catch (Exception e) {
            log.error("调用AI服务失败", e);
            return null;
        }
    }

    /**
     * 模拟AI回答（用于演示）
     * 实际项目中应该替换为真实的AI服务调用
     */
    private String generateMockResponse(String prompt) {
        // 模拟AI处理时间
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 简单的规则回答（实际应该由AI生成）
        if (prompt.toLowerCase().contains("双鱼玉佩")) {
            return "ANSWER_TYPE: MAYBE\nANSWER: 关于双鱼玉佩的具体信息，需要更多线索才能确定。";
        } else if (prompt.toLowerCase().contains("死亡") || prompt.toLowerCase().contains("杀")) {
            return "ANSWER_TYPE: NO\nANSWER: 根据现有线索，没有证据表明涉及死亡事件。";
        } else if (prompt.toLowerCase().contains("复制") || prompt.toLowerCase().contains("克隆")) {
            return "ANSWER_TYPE: YES\nANSWER: 是的，这个故事中确实涉及复制技术。";
        } else {
            return "ANSWER_TYPE: MAYBE\nANSWER: 需要更多信息来回答这个问题，请继续探索。";
        }
    }

    /**
     * 调用流式AI生成回答
     *
     * @param prompt 提示词
     * @param callback 流式回调
     */
    public void generateStreamResponse(String prompt, StreamCallback callback) {
        try {
            log.info("开始调用AI流式服务，提示词长度: {}", prompt.length());

            // TODO: 实现实际的流式AI服务调用
            // 示例实现
            String response = generateMockResponse(prompt);
            callback.onResponse(response);
            callback.onComplete();

        } catch (Exception e) {
            log.error("调用AI流式服务失败", e);
            callback.onError(e);
        }
    }

    /**
     * 流式回调接口
     */
    public interface StreamCallback {
        void onResponse(String response);
        void onComplete();
        void onError(Exception e);
    }
}