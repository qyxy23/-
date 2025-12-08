package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.VectorType;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.dto.QuestionRequest;
import com.guanyu.haigui.pojo.dto.QuestionResponse;
import com.guanyu.haigui.pojo.model.GameSession;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.result.ContextMatchResult;
import com.guanyu.haigui.repository.GameSessionRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.service.GameQuestionService;
import com.guanyu.haigui.service.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 游戏问题处理服务实现类
 * 实现向量检索和AI回答的完整流程
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameQuestionServiceImpl implements GameQuestionService {

    private final VectorService vectorService;
    private final GameSessionRepository gameSessionRepository;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final AIManager aiManager;

    @Override
    @Transactional
    public QuestionResponse processPlayerQuestion(QuestionRequest questionRequest) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始处理玩家问题: sessionId={}, soupId={}, question={}",
                    questionRequest.getSessionId(),
                    questionRequest.getSoupId(),
                    questionRequest.getQuestion().substring(0, Math.min(50, questionRequest.getQuestion().length())));

            // 1. 参数验证
            if (!validateRequest(questionRequest)) {
                return QuestionResponse.failure("请求参数无效");
            }

            // 2. 向量化玩家问题
            List<Float> questionVector = vectorizeQuestion(questionRequest.getQuestion());
            if (questionVector.isEmpty()) {
                return QuestionResponse.failure("问题向量化失败");
            }

            // 3. 检索相关上下文
            Map<String, List<ContextMatchResult>> contextMap = retrieveRelevantContext(
                    questionVector, questionRequest.getSoupId(), questionRequest.getTopK());

            // 4. 获取海龟汤信息
            SoupInfo soupInfo = getSoupInfo(questionRequest.getSoupId());

            // 5. 构建AI提示词
            String prompt = buildPromptForAI(questionRequest.getQuestion(), contextMap, soupInfo);

            // 6. 调用AI生成回答
            String aiAnswer = generateAIResponse(prompt);
            if (aiAnswer == null || aiAnswer.trim().isEmpty()) {
                return QuestionResponse.failure("AI回答生成失败");
            }

            // 7. 解析回答类型和相似度
            String answerType = parseAnswerType(aiAnswer);
            Double maxSimilarity = calculateMaxSimilarity(contextMap);

            // 8. 记录对话统计
            Long userId = questionRequest.getUserId() != null ?
                    questionRequest.getUserId() : BaseContext.getCurrentId();
            recordDialogStats(questionRequest.getSessionId(), userId, "YES".equalsIgnoreCase(answerType));

            // 9. 构建响应
            QuestionResponse response = QuestionResponse.success(aiAnswer, answerType, maxSimilarity);
            response.setProcessingTime(System.currentTimeMillis() - startTime);

            if (Boolean.TRUE.equals(questionRequest.getIncludeContext())) {
                response.setRelevantContext(contextMap);
            }

            // 10. 设置会话信息
            response.setSessionInfo(new QuestionResponse.SessionInfo(
                    questionRequest.getSessionId(),
                    questionRequest.getSoupId(),
                    soupInfo.getSoupTitle(),
                    soupInfo.getCurrentProgress()
            ));

            log.info("玩家问题处理完成: sessionId={}, 耗时={}ms, 回答类型={}",
                    questionRequest.getSessionId(), response.getProcessingTime(), answerType);

            return response;

        } catch (Exception e) {
            log.error("处理玩家问题失败: sessionId={}, soupId={}",
                    questionRequest.getSessionId(), questionRequest.getSoupId(), e);
            return QuestionResponse.failure("处理问题失败: " + e.getMessage());
        }
    }

    /**
     * 验证请求参数
     */
    private boolean validateRequest(QuestionRequest request) {
        return request.getSessionId() != null && !request.getSessionId().trim().isEmpty() &&
               request.getSoupId() != null && !request.getSoupId().trim().isEmpty() &&
               request.getQuestion() != null && !request.getQuestion().trim().isEmpty();
    }

    @Override
    public List<Float> vectorizeQuestion(String question) {
        return vectorService.vectorizeQuestion(question);
    }

    @Override
    public Map<String, List<ContextMatchResult>> retrieveRelevantContext(
            List<Float> questionVector, String soupId, int topK) {

        try {
            // 使用向量服务查找相关上下文
            Map<VectorType, List<ContextMatchResult>> contextResults =
                    vectorService.findRelevantContext("", soupId, topK);

            // 转换为字符串键的Map
            Map<String, List<ContextMatchResult>> resultMap = new HashMap<>();
            for (Map.Entry<VectorType, List<ContextMatchResult>> entry : contextResults.entrySet()) {
                resultMap.put(entry.getKey().name(), entry.getValue());
            }

            return resultMap;

        } catch (Exception e) {
            log.error("检索相关上下文失败: soupId={}", soupId, e);
            return Collections.emptyMap();
        }
    }

    @Override
    public String buildPromptForAI(String question, Map<String, List<ContextMatchResult>> contextMap, SoupInfo soupInfo) {
        StringBuilder prompt = new StringBuilder();



        // 2. 海龟汤基本信息
        prompt.append("当前游戏信息：\n");
        prompt.append("- 汤面：").append(soupInfo.getSoupSurface()).append("\n");
        prompt.append("- 当前进度：").append(soupInfo.getCurrentProgress()).append("\n\n");

        // 3. 相关上下文信息
        if (!contextMap.isEmpty()) {
            prompt.append("相关上下文信息（按相似度排序）：\n");

            // 处理不同类型的上下文
            for (Map.Entry<String, List<ContextMatchResult>> entry : contextMap.entrySet()) {
                String contextType = entry.getKey();
                List<ContextMatchResult> results = entry.getValue();

                if (!results.isEmpty()) {
                    prompt.append("\n【").append(getContextTypeDisplayName(contextType)).append("】\n");

                    for (int i = 0; i < results.size(); i++) {
                        ContextMatchResult result = results.get(i);
                        prompt.append(String.format("%d. 相似度%.2f: %s\n",
                                i + 1, result.getSimilarity(), result.getContent()));
                    }
                }
            }
            prompt.append("\n");
        }

        // 4. 玩家问题
        prompt.append("玩家问题：").append(question).append("\n\n");

        // 5. 回答要求
        prompt.append("请根据以上信息回答玩家的问题。请按以下格式回答：\n");
        prompt.append("ANSWER_TYPE: [YES/NO/MAYBE/DETAIL]\n");
        prompt.append("ANSWER: [你的回答]\n\n");
        prompt.append("注意：\n");
        prompt.append("- 如果确定，请用YES或NO\n");
        prompt.append("- 如果不确定，请用MAYBE并说明原因\n");
        prompt.append("- 如果需要提供详细解释，请用DETAIL\n");
        prompt.append("- 不要直接透露汤底的完整内容");

        return prompt.toString();
    }

    /**
     * 获取上下文类型的显示名称
     */
    private String getContextTypeDisplayName(String contextType) {
        return switch (contextType) {
            case "SURFACE" -> "汤面相关";
            case "BOTTOM" -> "汤底相关";
            case "MANUAL" -> "主持人手册";
            case "CLUE" -> "相关线索";
            default -> "其他信息";
        };
    }

    @Override
    public String generateAIResponse(String prompt) {
        try {
            log.info("开始调用AI生成回答，提示词长度: {}", prompt.length());

            String SystemPrompt = "你是一个海龟汤游戏的AI主持人。你需要根据玩家的问题，结合提供的上下文信息，给出准确的回答。" +
                    "游戏规则：" +
                    "- 对于可以用当前上下文明确回答的问题，直接给出YES或NO的回答" +
                    "- 如果信息不足，给出MAYBE并说明需要更多信息" +
                    "- 可以适当提供相关的细节信息，但不要直接透露汤底" +
                    "- 回答要简洁明了，避免冗长";

            String response = aiManager.doChat(SystemPrompt,prompt);

            if (response == null || response.trim().isEmpty()) {
                log.error("AI返回空回答");
                return null;
            }

            log.info("AI回答生成成功，回答长度: {}", response.length());
            return response;

        } catch (Exception e) {
            log.error("调用AI生成回答失败", e);
            return null;
        }
    }

    @Override
    @Transactional
    public void recordDialogStats(String sessionId, Long userId, boolean isYesAnswer) {
        try {
            // 记录对话统计信息
            log.info("记录对话统计: sessionId={}, userId={}, isYesAnswer={}",
                    sessionId, userId, isYesAnswer);

            // TODO: 实现完整的对话统计逻辑
            // 实际实现中应该：
            // 1. 查找或创建PlayerAiDialogStats记录
            // 2. 更新dialog_count和yes_count
            // 3. 保存到数据库
            // 4. 更新游戏会话状态

            // 暂时只记录日志，等待后续实现
            if (isYesAnswer) {
                log.info("用户回答为'是': sessionId={}, userId={}", sessionId, userId);
            } else {
                log.info("用户回答为'否'或其他: sessionId={}, userId={}", sessionId, userId);
            }

        } catch (Exception e) {
            log.error("记录对话统计失败: sessionId={}", sessionId, e);
        }
    }

    @Override
    public SoupInfo getSoupInfo(String soupId) {
        try {
            Optional<HaiGuiSoup> soupOpt = haiGuiSoupRepository.findById(soupId);
            if (soupOpt.isEmpty()) {
                log.warn("未找到海龟汤: soupId={}", soupId);
                return new SoupInfo(soupId, "未知海龟汤", "汤面信息不可用", "0%");
            }

            HaiGuiSoup soup = soupOpt.get();

            // 获取当前游戏进度
            String currentProgress = "0%";
            List<GameSession> sessions = gameSessionRepository
                    .findBySoupIdAndUserIdAndIsDeletedFalseOrderByStartTimeDesc(soupId, BaseContext.getCurrentId());

            if (!sessions.isEmpty()) {
                GameSession session = sessions.get(0);
                currentProgress = session.getCurrentProgress().toString() + "%";
            }

            return new SoupInfo(
                    soup.getSoupId(),
                    soup.getSoupTitle(),
                    soup.getSoupSurface(),
                    currentProgress
            );

        } catch (Exception e) {
            log.error("获取海龟汤信息失败: soupId={}", soupId, e);
            return new SoupInfo(soupId, "未知海龟汤", "汤面信息不可用", "0%");
        }
    }

    /**
     * 解析AI回答的类型
     */
    private String parseAnswerType(String aiAnswer) {
        if (aiAnswer == null || aiAnswer.trim().isEmpty()) {
            return "UNKNOWN";
        }

        String upperAnswer = aiAnswer.toUpperCase();

        // 查找ANSWER_TYPE行
        String[] lines = upperAnswer.split("\n");
        for (String line : lines) {
            if (line.startsWith("ANSWER_TYPE:")) {
                String type = line.substring("ANSWER_TYPE:".length()).trim();
                // 确保返回有效的类型
                if (type.matches("YES|NO|MAYBE|DETAIL")) {
                    return type;
                }
            }
        }

        // 如果没有找到ANSWER_TYPE，尝试从内容推断
        if (upperAnswer.contains("YES") || upperAnswer.contains("是")) {
            return "YES";
        } else if (upperAnswer.contains("NO") || upperAnswer.contains("不是") || upperAnswer.contains("否")) {
            return "NO";
        } else if (upperAnswer.contains("MAYBE") || upperAnswer.contains("可能") || upperAnswer.contains("不确定")) {
            return "MAYBE";
        } else {
            return "DETAIL";
        }
    }

    /**
     * 计算最高相似度
     */
    private Double calculateMaxSimilarity(Map<String, List<ContextMatchResult>> contextMap) {
        return contextMap.values().stream()
                .flatMap(List::stream)
                .map(ContextMatchResult::getSimilarity)
                .max(Double::compareTo)
                .orElse(0.0);
    }
}