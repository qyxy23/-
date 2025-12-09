package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 混合推理判断服务
 * 实现向量匹配+AI判断的混合方案
 */
@Service
@Slf4j
public class HybridInferenceService {

    private final AIManager aiManager;
    private final ObjectMapper objectMapper;
    private final BgeVectorClientUtil vectorClient;
    private final DynamicTaskService dynamicTaskService;
    private final ClueDecompositionService clueDecompositionService;

    // 配置参数
    @Value("${haiqutang.inference.vector_threshold:0.7}")
    private double defaultVectorThreshold;

    @Value("${haiqutang.inference.max_matched_fragments:3}")
    private int maxMatchedFragments;

    @Value("${haiqutang.inference.cache_enabled:true}")
    private boolean cacheEnabled;

    // 内存缓存（实际项目中使用Redis）
    private final Map<String, InferenceResult> resultCache = new HashMap<>();

    public HybridInferenceService(AIManager aiManager, ObjectMapper objectMapper,
                                BgeVectorClientUtil vectorClient, DynamicTaskService dynamicTaskService,
                                ClueDecompositionService clueDecompositionService) {
        this.aiManager = aiManager;
        this.objectMapper = objectMapper;
        this.vectorClient = vectorClient;
        this.dynamicTaskService = dynamicTaskService;
        this.clueDecompositionService = clueDecompositionService;
    }

    /**
     * 处理用户问题，返回推理结果
     */
    public InferenceResult processUserQuestion(Long userId, Long soupId, String question,
                                             List<ClueFragment> fragments, List<Map<String, Object>> tasks) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始处理用户问题，用户: {}, 汤ID: {}, 问题: {}", userId, soupId, question);

            // 1. 检查缓存
            String cacheKey = generateCacheKey(question, soupId);
            if (cacheEnabled && resultCache.containsKey(cacheKey)) {
                log.debug("命中缓存，直接返回结果");
                InferenceResult cachedResult = resultCache.get(cacheKey);
                cachedResult.setFromCache(true);
                return cachedResult;
            }

            // 2. 向量匹配阶段
            VectorMatchResult vectorResult = performVectorMatching(question, fragments);
            long vectorTime = System.currentTimeMillis() - startTime;

            // 3. AI判断阶段
            AIJudgmentResult aiResult = performAIJudgment(question, vectorResult, tasks);
            long aiTime = System.currentTimeMillis() - startTime - vectorTime;

            // 4. 生成最终结果
            InferenceResult result = buildFinalResult(question, vectorResult, aiResult, vectorTime, aiTime);

            // 5. 缓存结果
            if (cacheEnabled) {
                resultCache.put(cacheKey, result);
            }

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("问题处理完成，总耗时: {}ms (向量匹配: {}ms, AI判断: {}ms)",
                    totalTime, vectorTime, aiTime);

            return result;

        } catch (Exception e) {
            log.error("处理用户问题失败", e);
            return createErrorResult(e.getMessage());
        }
    }

    /**
     * 向量匹配阶段
     */
    private VectorMatchResult performVectorMatching(String question, List<ClueFragment> fragments) {
        try {
            // 生成问题向量
            SingleEncodeResponse response = BgeVectorClientUtil.encodeSingle(question);
            List<Float> questionVector = response.getEmbeddings().get(0);

            // 计算与每个片段的相似度
            List<FragmentMatch> matches = new ArrayList<>();
            for (ClueFragment fragment : fragments) {
                if (fragment.getVectorData() == null || fragment.getVectorData().isEmpty()) {
                    continue;
                }

                double similarity = calculateCosineSimilarity(questionVector, fragment.getVectorData());
                if (similarity >= fragment.getSimilarityThreshold()) {
                    FragmentMatch match = new FragmentMatch();
                    match.setFragment(fragment);
                    match.setSimilarity(similarity);
                    match.setThreshold(fragment.getSimilarityThreshold());
                    matches.add(match);
                }
            }

            // 按相似度排序，取TOP N
            matches.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
            List<FragmentMatch> topMatches = matches.stream()
                    .limit(maxMatchedFragments)
                    .collect(Collectors.toList());

            VectorMatchResult result = new VectorMatchResult();
            result.setQuestionVector(questionVector);
            result.setMatches(topMatches);
            result.setTotalMatches(matches.size());

            log.debug("向量匹配完成，匹配到{}个片段，阈值: {}", topMatches.size(), defaultVectorThreshold);
            return result;

        } catch (Exception e) {
            log.error("向量匹配失败", e);
            return new VectorMatchResult(); // 返回空结果
        }
    }

    /**
     * AI判断阶段
     */
    @SuppressWarnings("unchecked")
    private AIJudgmentResult performAIJudgment(String question, VectorMatchResult vectorResult,
                                             List<Map<String, Object>> tasks) {
        try {
            // 构建AI提示词
            String prompt = buildAIPrompt(question, vectorResult, tasks);

            // 调用AI进行判断
            String systemPrompt = "你是专业的海龟汤推理分析师，擅长判断玩家的理解深度和推理进度。请客观分析玩家的问题，评估其对各个推理任务的理解程度。";
            String aiResponse = aiManager.doChat(systemPrompt, prompt);

            // 解析AI响应
            Map<String, Object> response = objectMapper.readValue(aiResponse, Map.class);

            AIJudgmentResult result = new AIJudgmentResult();
            result.setPrompt(prompt);
            result.setResponse(aiResponse);
            result.setTaskProgresses((List<Map<String, Object>>) response.get("taskProgresses"));
            result.setOverallUnderstanding(((Number) response.getOrDefault("overallUnderstanding", 0)).doubleValue());
            result.setKeyInsights((List<String>) response.getOrDefault("keyInsights", new ArrayList<>()));
            result.setRecommendation((String) response.get("recommendation"));

            log.debug("AI判断完成，整体理解度: {}%", result.getOverallUnderstanding());
            return result;

        } catch (Exception e) {
            log.error("AI判断失败", e);
            return createFallbackAIResult();
        }
    }

    /**
     * 构建AI判断提示词
     */
    private String buildAIPrompt(String question, VectorMatchResult vectorResult, List<Map<String, Object>> tasks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请分析玩家的问题，评估其对海龟汤推理任务的理解程度。\n\n");

        prompt.append("=== 玩家问题 ===\n");
        prompt.append(question).append("\n\n");

        prompt.append("=== 向量匹配结果 ===\n");
        if (vectorResult.getMatches().isEmpty()) {
            prompt.append("未匹配到相关线索片段\n\n");
        } else {
            for (int i = 0; i < vectorResult.getMatches().size(); i++) {
                FragmentMatch match = vectorResult.getMatches().get(i);
                prompt.append(String.format("%d. 片段内容：%s\n", i + 1, match.getFragment().getFragmentContent()));
                prompt.append(String.format("   相似度：%.2f (阈值: %.2f)\n", match.getSimilarity(), match.getThreshold()));
                prompt.append(String.format("   类型：%s, 推理层次：%d\n\n",
                        match.getFragment().getFragmentType(), match.getFragment().getInferenceLevel()));
            }
        }

        prompt.append("=== 推理任务列表 ===\n");
        for (int i = 0; i < tasks.size(); i++) {
            Map<String, Object> task = tasks.get(i);
            prompt.append(String.format("任务%d：%s\n", i + 1, task.get("taskName")));
            prompt.append(String.format("目标：%s\n", task.get("description")));
            prompt.append(String.format("权重：%.1f\n\n", ((Number) task.get("progressWeight")).doubleValue()));
        }

        prompt.append("=== 分析要求 ===\n");
        prompt.append("请根据玩家问题和向量匹配结果，分析以下内容：\n");
        prompt.append("1. 玩家的问题表明他对哪些任务有了新的理解？\n");
        prompt.append("2. 每个相关任务的进度应该如何调整？（0-100分）\n");
        prompt.append("3. 玩家的整体理解程度如何？（0-100分）\n");
        prompt.append("4. 从问题中能发现什么关键洞察？\n");
        prompt.append("5. 对玩家下一步的建议？\n\n");

        prompt.append("=== 输出要求 ===\n");
        prompt.append("请严格按照以下JSON格式返回：\n");
        prompt.append("{\n");
        prompt.append("  \"taskProgresses\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"taskId\": 1,\n");
        prompt.append("      \"taskName\": \"任务名称\",\n");
        prompt.append("      \"progressChange\": +25,\n");
        prompt.append("      \"newProgress\": 75,\n");
        prompt.append("      \"isCompleted\": false,\n");
        prompt.append("      \"reason\": \"进度变化的原因\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"overallUnderstanding\": 65,\n");
        prompt.append("  \"keyInsights\": [\"洞察1\", \"洞察2\"],\n");
        prompt.append("  \"recommendation\": \"给玩家的建议\"\n");
        prompt.append("}\n");

        prompt.append("\n重要说明：\n");
        prompt.append("- progressChange: 本次问题带来的进度变化（可为负数）\n");
        prompt.append("- newProgress: 调整后的总进度（0-100）\n");
        prompt.append("- isCompleted: 是否完成该任务（达到100分）\n");
        prompt.append("- 只有真正体现理解的问题才能增加进度\n");
        prompt.append("- 机械重复或无关问题不应增加进度\n");

        return prompt.toString();
    }

    /**
     * 构建最终结果
     */
    private InferenceResult buildFinalResult(String question, VectorMatchResult vectorResult,
                                           AIJudgmentResult aiResult, long vectorTime, long aiTime) {
        InferenceResult result = new InferenceResult();
        result.setQuestion(question);
        result.setVectorMatchResult(vectorResult);
        result.setAiJudgmentResult(aiResult);
        result.setVectorMatchTime(vectorTime);
        result.setAiJudgmentTime(aiTime);
        result.setTotalProcessingTime(vectorTime + aiTime);
        result.setFromCache(false);
        result.setTimestamp(System.currentTimeMillis());

        // 生成用户回答
        result.setUserResponse(generateUserResponse(aiResult, vectorResult));

        return result;
    }

    /**
     * 生成给用户的回答
     */
    private String generateUserResponse(AIJudgmentResult aiResult, VectorMatchResult vectorResult) {
        StringBuilder response = new StringBuilder();

        // 基于匹配的线索片段给出提示
        if (!vectorResult.getMatches().isEmpty()) {
            response.append("你的问题触及了一些重要线索。\n\n");

            FragmentMatch bestMatch = vectorResult.getMatches().get(0);
            response.append("主要相关线索：").append(bestMatch.getFragment().getFragmentContent()).append("\n");

            if (bestMatch.getSimilarity() > 0.9) {
                response.append("你已经很接近真相了！继续深入思考这个方向。\n");
            } else if (bestMatch.getSimilarity() > 0.8) {
                response.append("方向正确，但还需要更深入的理解。\n");
            } else {
                response.append("这是一个值得探索的方向，试着问得更具体一些。\n");
            }
        } else {
            response.append("这个问题似乎没有触及关键线索。试着从时间、地点、人物或事件的角度提问。\n");
        }

        // 添加AI建议
        if (aiResult.getRecommendation() != null && !aiResult.getRecommendation().isEmpty()) {
            response.append("\n建议：").append(aiResult.getRecommendation());
        }

        return response.toString();
    }

    /**
     * 计算余弦相似度
     */
    private double calculateCosineSimilarity(List<Float> vec1, List<Float> vec2) {
        if (vec1.size() != vec2.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += Math.pow(vec1.get(i), 2);
            norm2 += Math.pow(vec2.get(i), 2);
        }

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 生成缓存键
     */
    private String generateCacheKey(String question, Long soupId) {
        try {
            String key = soupId + ":" + question.toLowerCase().trim();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(key.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "cache_" + soupId + "_" + question.hashCode();
        }
    }

    /**
     * 创建备用AI结果
     */
    private AIJudgmentResult createFallbackAIResult() {
        AIJudgmentResult result = new AIJudgmentResult();
        result.setOverallUnderstanding(0.0);
        result.setTaskProgresses(new ArrayList<>());
        result.setKeyInsights(new ArrayList<>());
        result.setRecommendation("系统暂时无法准确评估你的理解程度，请继续提问。");
        return result;
    }

    /**
     * 创建错误结果
     */
    private InferenceResult createErrorResult(String errorMessage) {
        InferenceResult result = new InferenceResult();
        result.setError(true);
        result.setErrorMessage(errorMessage);
        result.setUserResponse("抱歉，处理你的问题时出现了错误，请稍后重试。");
        return result;
    }

    // 内部数据类
    @Data
    public static class VectorMatchResult {
        private List<Float> questionVector;
        private List<FragmentMatch> matches;
        private int totalMatches;
    }

    @Data
    public static class FragmentMatch {
        private ClueFragment fragment;
        private double similarity;
        private double threshold;
    }

    @Data
    public static class AIJudgmentResult {
        private String prompt;
        private String response;
        private List<Map<String, Object>> taskProgresses;
        private double overallUnderstanding;
        private List<String> keyInsights;
        private String recommendation;
    }

    @Data
    public static class InferenceResult {
        private String question;
        private VectorMatchResult vectorMatchResult;
        private AIJudgmentResult aiJudgmentResult;
        private String userResponse;
        private long vectorMatchTime;
        private long aiJudgmentTime;
        private long totalProcessingTime;
        private boolean fromCache;
        private boolean error;
        private String errorMessage;
        private long timestamp;
    }
}