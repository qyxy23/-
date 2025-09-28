package com.guanyu.haigui;


import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// @Component
@SpringBootTest
class AiTest {
    @Value("${ai.apiKey}")
    private String apiKey;

    // 配置参数（可根据需要调整）
    // private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
    // private ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
    // private Dispatcher dispatcher = new Dispatcher();
    //
    // private ArkService service; // 声明但不直接初始化

    // @PostConstruct：在依赖注入完成后初始化 ArkService
    // @PostConstruct
    // public void initArkService() {
    //     // 此时 apiKey 已被 Spring 正确注入
    //     service = ArkService.builder()
    //             .dispatcher(dispatcher)
    //             .connectionPool(connectionPool)
    //             .baseUrl(baseUrl)
    //             .apiKey(apiKey) // 关键：使用注入后的 apiKey
    //             .build();
    // }


    @Test
    public void doTest() {
        // if (service == null) {
        //     throw new IllegalStateException("ArkService 未初始化，请检查依赖注入");
        // }
        // 此为默认路径，您可根据业务所在地域进行配置
        String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool).baseUrl(baseUrl).apiKey(apiKey).build();

        // ------------------------- 同步请求测试 -------------------------
        // System.out.println("\n----- standard request -----");
        // List<ChatMessage> messages = new ArrayList<>();
        // messages.add(ChatMessage.builder()
        //         .role(ChatMessageRole.SYSTEM)
        //         .content("你是人工智能助手。")
        //         .build());
        // messages.add(ChatMessage.builder()
        //         .role(ChatMessageRole.USER)
        //         .content("你好")
        //         .build());
        //
        // ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
        //         .model("ep-20250913203623-2bv46") // 确认模型 ID 正确
        //         .messages(messages)
        //         .build();
        //
        // try {
        //     service.createChatCompletion(chatRequest)
        //             .getChoices()
        //             .forEach(choice -> System.out.println(choice.getMessage().getContent()));
        // } catch (Exception e) {
        //     e.printStackTrace(); // 捕获并打印异常（如网络问题、模型不存在等）
        // }

        // ------------------------- 流式请求测试 -------------------------
        System.out.println("\n----- streaming request -----");
        List<ChatMessage> streamMessages = new ArrayList<>();
        streamMessages.add(ChatMessage.builder()
                .role(ChatMessageRole.SYSTEM)
                .content("你是人工智能")
                .build());
        streamMessages.add(ChatMessage.builder()
                .role(ChatMessageRole.USER)
                .content("hello")
                .build());

        ChatCompletionRequest streamRequest = ChatCompletionRequest.builder()
                .model("ep-20250913203623-2bv46") // 确认模型 ID 正确
                .messages(streamMessages) // 修正：使用流式专用的消息列表
                .build();

        service.streamChatCompletion(streamRequest)
                .doOnError(Throwable::printStackTrace) // 打印流式请求错误
                .blockingForEach(choice -> {
                    if (!choice.getChoices().isEmpty()) {
                        System.out.print(choice.getChoices().get(0).getMessage().getContent());
                    }
                });


        // 关闭线程池（确保资源释放）
        service.shutdownExecutor();
    }
}



