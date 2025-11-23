package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.QuestionRequest;
import com.guanyu.haigui.pojo.dto.QuestionResponse;
import com.guanyu.haigui.service.GameQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 游戏控制器
 * 提供海龟汤游戏的核心功能接口
 */
@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
@Slf4j
public class GameController {

    private final GameQuestionService gameQuestionService;

    /**
     * 处理玩家问题
     *
     * @param questionRequest 问题请求
     * @return 问题回答响应
     */
    @PostMapping("/question")
    public ResponseEntity<QuestionResponse> askQuestion(@RequestBody QuestionRequest questionRequest) {
        try {
            log.info("收到玩家问题请求: sessionId={}, soupId={}",
                    questionRequest.getSessionId(), questionRequest.getSoupId());

            QuestionResponse response = gameQuestionService.processPlayerQuestion(questionRequest);

            if (response.getSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("处理玩家问题失败", e);
            return ResponseEntity.internalServerError()
                    .body(QuestionResponse.failure("服务器内部错误: " + e.getMessage()));
        }
    }

    /**
     * 简化的提问接口（仅需必要参数）
     *
     * @param sessionId 游戏会话ID
     * @param soupId 海龟汤ID
     * @param question 玩家问题
     * @return 问题回答响应
     */
    @PostMapping("/question/simple")
    public ResponseEntity<QuestionResponse> askQuestionSimple(
            @RequestParam String sessionId,
            @RequestParam String soupId,
            @RequestParam String question) {

        QuestionRequest request = new QuestionRequest();
        request.setSessionId(sessionId);
        request.setSoupId(soupId);
        request.setQuestion(question);
        request.setTopK(5);
        request.setIncludeContext(false);

        return askQuestion(request);
    }

    /**
     * 带上下文的提问接口
     *
     * @param sessionId 游戏会话ID
     * @param soupId 海龟汤ID
     * @param question 玩家问题
     * @param topK 检索上下文数量
     * @return 问题回答响应
     */
    @PostMapping("/question/context")
    public ResponseEntity<QuestionResponse> askQuestionWithContext(
            @RequestParam String sessionId,
            @RequestParam String soupId,
            @RequestParam String question,
            @RequestParam(defaultValue = "10") Integer topK) {

        QuestionRequest request = new QuestionRequest();
        request.setSessionId(sessionId);
        request.setSoupId(soupId);
        request.setQuestion(question);
        request.setTopK(topK);
        request.setIncludeContext(true);

        return askQuestion(request);
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Game service is running");
    }
}