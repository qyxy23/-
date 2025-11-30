package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.VectorType;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.Info.SoupInfo;
import com.guanyu.haigui.pojo.dto.SoupQuestionRequest;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.InferenceTask;
import com.guanyu.haigui.pojo.vo.SoupQuestionResponse;
import com.guanyu.haigui.repository.ClueFragmentRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.InferenceTaskRepository;
import com.guanyu.haigui.service.SoupQuestionService;
import com.guanyu.haigui.service.VectorService;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 海龟汤问题处理服务实现类
 * 基于向量匹配和AI判断的智能问答系统
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SoupQuestionServiceImpl implements SoupQuestionService {

    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final ClueFragmentRepository clueFragmentRepository;
    private final InferenceTaskRepository inferenceTaskRepository;
    private final BgeVectorClientUtil bgeVectorClientUtil;
    private final RedisStackClient redisClient;
    private final AIManager aiManager;


    @Override
    @Transactional
    public SoupQuestionResponse processSoupQuestion1(SoupQuestionRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始处理海龟汤问题: soupId={}, question={}",
                    request.getSoupId(),
                    request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));

            // 1. 参数验证
            if (!validateRequest(request)) {
                return SoupQuestionResponse.failure("请求参数无效");
            }

            // 2. 向量化问题
            List<Float> questionVector = vectorizeQuestion(request.getQuestion());
            if (questionVector.isEmpty()) {
                return SoupQuestionResponse.failure("问题向量化失败");
            }

            log.info("问题向量化成功，维度: {}", questionVector.size());

            // 3. 搜索相关线索
            Map<String, List<VectorService.ContextMatchResult>> relevantClues = searchRelevantClues(
                    request.getSoupId(),
                    questionVector,
                    request.getTopK(),
                    request.getMinSimilarity(),
                    request.getQuestion()
            );
            log.info("搜索相关线索成功，数量: {}", relevantClues.size());

            // 4. 获取海龟汤信息
            SoupInfo soupInfo = getSoupInfo(request.getSoupId());

            // 5. 构建AI提示词
            String aiPrompt = buildAIPrompt(request.getQuestion(), relevantClues, soupInfo);

            log.info("构建AI提示词成功: {}", aiPrompt);


            // 6. 调用AI生成判断
            String aiResponse = generateAIResponse(aiPrompt);
            if (aiResponse == null || aiResponse.trim().isEmpty()) {
                return SoupQuestionResponse.failure("AI判断生成失败");
            }

            // 7. 解析AI判断结果
            String answer = parseAnswer(aiResponse);

            // 8. 计算最高相似度
            double maxSimilarity = calculateMaxSimilarity(relevantClues);

            // 9. 构建响应
            SoupQuestionResponse response = SoupQuestionResponse.success(
                    request.getSoupId(),
                    request.getQuestion(),
                    answer,
                    generateExplanation(answer, relevantClues, aiResponse)
            );

            // 设置处理时间
            response.setProcessingTime(System.currentTimeMillis() - startTime);

            // 设置匹配详情
            if (Boolean.TRUE.equals(request.getIncludeMatchDetails())) {
                response.setMatchedClues(buildMatchedClues(relevantClues));
                response.setVectorMatches(buildVectorMatches(relevantClues));
            }
            response.setMaxSimilarity(maxSimilarity > 0 ? maxSimilarity : null);
            response.setMatchedClueCount(calculateMatchedClueCount(relevantClues));

            // 设置会话信息
            response.setSessionInfo(new SoupQuestionResponse.SessionInfo(
                    "soup-question-" + System.currentTimeMillis(),
                    soupInfo.getSoupTitle(),
                    soupInfo.getCurrentProgress()
            ));

            // 10. 记录统计信息
            Long userId = request.getUserId() != null ? request.getUserId() : BaseContext.getCurrentId();
            recordQuestionStats(request.getSoupId(), userId, request.getQuestion(), answer, maxSimilarity);

            log.info("海龟汤问题处理完成: soupId={}, 耗时={}ms, 判断结果={}, 最高相似度={}",
                    request.getSoupId(), response.getProcessingTime(), answer, maxSimilarity);

            return response;

        } catch (Exception e) {
            log.error("处理海龟汤问题失败: soupId={}, question={}",
                    request.getSoupId(), request.getQuestion(), e);
            return SoupQuestionResponse.failure("处理失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public String processSoupQuestion(SoupQuestionRequest request) {

        log.info("开始处理海龟汤问题: soupId={}, question={}",
                request.getSoupId(),
                request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));

        // 2. 向量化问题
        List<Float> questionVector = vectorizeQuestion(request.getQuestion());


        log.info("问题向量化成功，维度: {}", questionVector.size());

        // 3. 搜索相关线索
        Map<String, List<VectorService.ContextMatchResult>> relevantClues = searchRelevantClues(
                request.getSoupId(),
                questionVector,
                request.getTopK(),
                request.getMinSimilarity(),
                request.getQuestion()
        );
        log.info("搜索相关线索成功，数量: {}", relevantClues.size());

        // 4. 获取海龟汤信息
        SoupInfo soupInfo = getSoupInfo(request.getSoupId());

        // 5. 构建AI提示词
        String aiPrompt = buildAIPrompt(request.getQuestion(), relevantClues, soupInfo);

        log.info("构建AI提示词成功: {}", aiPrompt);

        return aiPrompt;
    }

    @Override
    public boolean validateRequest(SoupQuestionRequest request) {
        if (request == null) {
            log.warn("请求对象为空");
            return false;
        }

        if (request.getSoupId() == null || request.getSoupId().trim().isEmpty()) {
            log.warn("海龟汤ID为空");
            return false;
        }

        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            log.warn("问题内容为空");
            return false;
        }

        if (request.getTopK() != null && (request.getTopK() <= 0 || request.getTopK() > 20)) {
            log.warn("topK参数超出范围: {}", request.getTopK());
            return false;
        }

        if (request.getMinSimilarity() != null && (request.getMinSimilarity() < 0 || request.getMinSimilarity() > 1)) {
            log.warn("minSimilarity参数超出范围: {}", request.getMinSimilarity());
            return false;
        }

        return true;
    }

    @Override
    public List<Float> vectorizeQuestion(String question) {
        try {
            log.debug("开始向量化问题: {}", question.substring(0, Math.min(30, question.length())));

            // 使用BGE模型向量化
            com.guanyu.haigui.pojo.vo.SingleEncodeResponse response = bgeVectorClientUtil.encodeSingle(question);
            if (response == null || response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
                log.error("BGE向量化失败: {}", question);
                return Collections.emptyList();
            }

            List<Float> vector = response.getEmbeddings().get(0);
            log.debug("问题向量化成功，维度: {}", vector.size());

            return vector;

        } catch (Exception e) {
            log.error("向量化问题失败: {}", question, e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, List<VectorService.ContextMatchResult>> searchRelevantClues(String soupId,
                                                                                   List<Float> questionVector,
                                                                                   int topK,
                                                                                   double minSimilarity,
                                                                                   String question) {
        try {
            log.info("开始搜索相关线索: soupId={}, topK={}, minSimilarity={}, question={}",
                    soupId, topK, minSimilarity, question.substring(0, Math.min(30, question.length())));

            // 使用Redis在指定海龟汤中搜索相似片段（参考searchCluesInSoup的逻辑）
            Map<String, Double> fragmentResults = redisClient.searchSimilarCluesInSoup(
                    questionVector, soupId, topK * 2); // 搜索更多，后续过滤

            // 获取片段详细信息并转换为ContextMatchResult
            Map<String, List<VectorService.ContextMatchResult>> results = new HashMap<>();
            List<VectorService.ContextMatchResult> fragmentMatchResults = new ArrayList<>();

            for (Map.Entry<String, Double> entry : fragmentResults.entrySet()) {
                String fragmentId = entry.getKey();
                Double similarity = entry.getValue();

                // 过滤低于阈值的匹配结果
                if (similarity < minSimilarity) {
                    continue;
                }

                try {
                    Long fragId = Long.parseLong(fragmentId);
                    ClueFragment fragment = clueFragmentRepository.findById(fragId).orElse(null);
                    if (fragment != null) {
                        // 创建ContextMatchResult
                        VectorService.ContextMatchResult result = new VectorService.ContextMatchResult(
                                fragmentId,
                                fragment.getFragmentContent(),
                                similarity,
                                VectorType.CLUE
                        );
                        fragmentMatchResults.add(result);
                    }
                } catch (NumberFormatException e) {
                    log.warn("无法解析片段ID: {}", fragmentId);
                }
            }

            // 按相似度排序并限制数量
            fragmentMatchResults.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
            if (fragmentMatchResults.size() > topK) {
                fragmentMatchResults = fragmentMatchResults.subList(0, topK);
            }

            // 如果找到匹配的fragment，添加到结果中
            if (!fragmentMatchResults.isEmpty()) {
                results.put("CLUE_FRAGMENT", fragmentMatchResults);
            }

            int totalMatches = fragmentMatchResults.size();
            log.info("线索搜索完成: 总匹配数={}", totalMatches);

            return results;

        } catch (Exception e) {
            log.error("搜索相关线索失败: soupId={}", soupId, e);
            return Collections.emptyMap();
        }
    }

    @Override
    public String buildAIPrompt(String question,
                                Map<String, List<VectorService.ContextMatchResult>> relevantClues,
                                SoupInfo soupInfo) {
        StringBuilder prompt = new StringBuilder();



        // 海龟汤背景信息
        prompt.append("=== 海龟汤背景 ===\n");
        prompt.append(String.format("标题：%s\n", soupInfo.getSoupTitle()));
        prompt.append(String.format("汤面：%s\n", soupInfo.getSoupSurface()));
        prompt.append(String.format("汤底：%s\n", soupInfo.getSoupBottom()));
        prompt.append(String.format("主持人手册：%s\n\n", soupInfo.getHostManual()));

        // 获取相关任务信息
        Map<Integer, InferenceTask> relevantTasks = getRelevantTasks(relevantClues);

        // 相关推理任务信息
        prompt.append("=== 相关推理任务 ===\n");
        if (relevantTasks.isEmpty()) {
            prompt.append("未找到直接相关的推理任务。\n\n");
        } else {
            int taskIndex = 1;
            for (Map.Entry<Integer, InferenceTask> entry : relevantTasks.entrySet()) {
                InferenceTask task = entry.getValue();
                prompt.append(String.format("%d. [任务ID:%d] %s\n",
                        taskIndex++,
                        task.getTaskId(),
                        task.getTaskName()));
                prompt.append(String.format("   描述：%s\n", task.getTaskDescription()));
                prompt.append(String.format("   理解层次：%d，权重：%.1f\n",
                        task.getUnderstandingLevel(),
                        task.getProgressWeight()));

                if (task.getTargetKeywords() != null && !task.getTargetKeywords().isEmpty()) {
                    prompt.append(String.format("   目标关键词：%s\n", String.join("、", task.getTargetKeywords())));
                }
                prompt.append(String.format("   推理目标：%s\n\n", task.getReasoningGoal()));
            }
        }

        // 相关线索信息
        prompt.append("=== 相关线索信息 ===\n");
        if (relevantClues.isEmpty()) {
            prompt.append("未找到直接相关的线索。\n");
        } else {
            int clueIndex = 1;
            for (Map.Entry<String, List<VectorService.ContextMatchResult>> entry : relevantClues.entrySet()) {
                prompt.append(String.format("【%s类型线索】\n", entry.getKey()));
                for (VectorService.ContextMatchResult result : entry.getValue()) {
                    // 获取该线索关联的任务ID
                    List<Integer> associatedTaskIds = getAssociatedTaskIds(result.getId());
                    String taskInfo = associatedTaskIds.isEmpty() ?
                            "" : String.format(" (关联任务ID: %s)",
                            associatedTaskIds.stream()
                                    .map(String::valueOf)
                                    .collect(Collectors.joining(", ")));

                    prompt.append(String.format("%d. %s (相似度: %.2f)%s\n",
                            clueIndex++,
                            result.getContent(),
                            result.getSimilarity(),
                            taskInfo));
                }
                prompt.append("\n");
            }
        }

        // 问题
        prompt.append("=== 玩家问题 ===\n");
        prompt.append(question).append("\n\n");

        // 输出要求
        prompt.append("=== 回答要求 ===\n");
        prompt.append("请基于上述线索和推理任务，对玩家的问题给出判断。回答格式如下：\n");
        prompt.append("ANSWER: [是/不是/是或不是/不重要]\n");
        prompt.append("EXPLANATION: [详细的解释说明]\n");
        prompt.append("AFFECTED_TASKS: [任务ID1,任务ID2,...] (被此问题影响或推进的任务ID列表)\n\n");
        prompt.append("说明：\n");
        prompt.append("- 是: 线索明确支持问题的肯定回答\n");
        prompt.append("- 不是: 线索明确支持问题的否定回答\n");
        prompt.append("- 是或不是: 线索部分支持，但不能完全确定\n");
        prompt.append("- 不重要: 线索不足以判断\n");
        prompt.append("- AFFECTED_TASKS: 列出此问题直接关联或推进的推理任务ID，用于进度更新\n");

        return prompt.toString();
    }

    @Override
    public String generateAIResponse(String prompt) {
        try {
            log.debug("调用AI生成判断，提示词长度: {}", prompt.length());


            // 系统角色定义
            String systemPrompt = "你是一个海龟汤游戏的AI助手,专门负责根据线索判断玩家问题的答案,请基于提供的线索信息,对玩家的问题给出准确的判断。";
            String response = aiManager.doChat(systemPrompt,prompt);

            if (response == null || response.trim().isEmpty()) {
                log.error("AI返回空响应");
                return null;
            }

            log.debug("AI响应成功，长度: {}", response.length());
            return response;

        } catch (Exception e) {
            log.error("调用AI生成判断失败", e);
            return null;
        }
    }

    @Override
    public String parseAnswer(String aiResponse) {
        try {
            log.debug("解析AI回答: {}", aiResponse.substring(0, Math.min(100, aiResponse.length())));

            // 尝试从ANSWER字段提取结果
            if (aiResponse.contains("ANSWER:")) {
                String answerLine = aiResponse.lines()
                        .filter(line -> line.startsWith("ANSWER:"))
                        .findFirst()
                        .orElse("");

                String answer = answerLine.replace("ANSWER:", "").trim().toUpperCase();

                if (answer.equals("是") || answer.equals("不是") ||
                        answer.equals("是或不是") || answer.equals("不重要")) {
                    log.debug("解析到有效回答: {}", answer);
                    return answer;
                }
            }

            // 如果无法解析，尝试简单文本匹配
            String response = aiResponse.toLowerCase();
            if (response.contains("是") || response.contains("yes") || response.contains("肯定")) {
                return "YES";
            } else if (response.contains("否") || response.contains("no") || response.contains("不是")) {
                return "NO";
            } else if (response.contains("部分") || response.contains("可能") || response.contains("partial")) {
                return "PARTIAL";
            }

            log.warn("无法解析AI回答，返回UNKNOWN: {}", aiResponse);
            return "UNKNOWN";

        } catch (Exception e) {
            log.error("解析AI回答失败", e);
            return "UNKNOWN";
        }
    }

    @Override
    public SoupInfo getSoupInfo(String soupId) {
        try {
            HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElse(null);
            if (soup == null) {
                log.warn("海龟汤不存在: {}", soupId);
                return null;
            }

            // 可以在这里添加进度计算逻辑
            // soupInfo.setCurrentProgress(calculateProgress(soupId));

            return new SoupInfo(
                    soup.getSoupId(),
                    soup.getSoupTitle(),
                    soup.getSoupSurface(),
                    soup.getSoupBottom(),
                    soup.getHostManual()
            );

        } catch (Exception e) {
            log.error("获取海龟汤信息失败: soupId={}", soupId, e);
            return null;
        }
    }

    @Override
    public void recordQuestionStats(String soupId, Long userId, String question, String answer, Double similarity) {
        try {
            // 这里可以实现统计信息的记录
            // 例如记录到数据库或日志中
            log.info("记录问答统计: soupId={}, userId={}, question={}, answer={}, similarity={}",
                    soupId, userId, question.substring(0, Math.min(30, question.length())), answer, similarity);

        } catch (Exception e) {
            log.error("记录问答统计失败", e);
        }
    }

    /**
     * 计算最高相似度
     */
    private double calculateMaxSimilarity(Map<String, List<VectorService.ContextMatchResult>> relevantClues) {
        return relevantClues.values().stream()
                .flatMap(List::stream)
                .mapToDouble(VectorService.ContextMatchResult::getSimilarity)
                .max()
                .orElse(0.0);
    }

    /**
     * 生成解释说明
     */
    private String generateExplanation(String answer,
                                       Map<String, List<VectorService.ContextMatchResult>> relevantClues,
                                       String aiResponse) {
        try {
            // 尝试从AI响应中提取EXPLANATION
            if (aiResponse.contains("EXPLANATION:")) {
                return aiResponse.lines()
                        .filter(line -> line.startsWith("EXPLANATION:"))
                        .findFirst()
                        .map(line -> line.replace("EXPLANATION:", "").trim())
                        .orElse(generateDefaultExplanation(answer, relevantClues));
            }

            return generateDefaultExplanation(answer, relevantClues);

        } catch (Exception e) {
            log.error("生成解释说明失败", e);
            return "基于相关线索的判断结果";
        }
    }

    /**
     * 生成默认解释说明
     */
    private String generateDefaultExplanation(String answer,
                                              Map<String, List<VectorService.ContextMatchResult>> relevantClues) {
        StringBuilder explanation = new StringBuilder();

        switch (answer) {
            case "YES":
                explanation.append("根据相关线索显示，问题的答案是肯定的。");
                break;
            case "NO":
                explanation.append("根据相关线索显示，问题的答案是否定的。");
                break;
            case "PARTIAL":
                explanation.append("根据相关线索显示，问题部分正确，但不能完全确定。");
                break;
            case "UNKNOWN":
                explanation.append("现有线索不足以判断问题的答案。");
                break;
        }

        // 添加匹配到的线索类型信息
        long clueTypeCount = relevantClues.values().stream()
                .flatMap(List::stream)
                .map(result -> result.getType())
                .distinct()
                .count();

        if (clueTypeCount > 0) {
            explanation.append(String.format(" 涉及%d种类型的线索。", clueTypeCount));
        }

        return explanation.toString();
    }

    /**
     * 构建匹配的线索信息
     */
    private List<SoupQuestionResponse.ClueMatchInfo> buildMatchedClues(
            Map<String, List<VectorService.ContextMatchResult>> relevantClues) {

        return relevantClues.values().stream()
                .flatMap(List::stream)
                .map(result -> SoupQuestionResponse.ClueMatchInfo.builder()
                        .clueId(result.getId())
                        .clueContent(result.getContent())
                        .clueType(result.getType().toString())
                        .similarity(result.getSimilarity())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 构建向量匹配结果
     */
    private Map<String, List<SoupQuestionResponse.VectorMatchResult>> buildVectorMatches(
            Map<String, List<VectorService.ContextMatchResult>> relevantClues) {

        return relevantClues.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(result -> SoupQuestionResponse.VectorMatchResult.builder()
                                        .vectorType(result.getType().toString())
                                        .contentId(result.getId())
                                        .content(result.getContent())
                                        .similarity(result.getSimilarity())
                                        .build())
                                .collect(Collectors.toList())
                ));
    }

    /**
     * 计算匹配的线索总数
     */
    private int calculateMatchedClueCount(Map<String, List<VectorService.ContextMatchResult>> relevantClues) {
        return relevantClues.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * 获取相关推理任务信息
     */
    private Map<Integer, InferenceTask> getRelevantTasks(Map<String, List<VectorService.ContextMatchResult>> relevantClues) {
        Map<Integer, InferenceTask> relevantTasks = new HashMap<>();

        try {
            // 收集所有相关的任务ID
            Set<Long> taskIds = new HashSet<>();
            for (List<VectorService.ContextMatchResult> results : relevantClues.values()) {
                for (VectorService.ContextMatchResult result : results) {
                    List<Integer> associatedTaskIds = getAssociatedTaskIds(result.getId());
                    for (Integer taskId : associatedTaskIds) {
                        taskIds.add(taskId.longValue());
                    }
                }
            }

            if (taskIds.isEmpty()) {
                return relevantTasks;
            }

            // 批量查询相关任务
            List<InferenceTask> tasks = inferenceTaskRepository.findByTaskIdInAndIsDeletedFalse(new ArrayList<>(taskIds));
            for (InferenceTask task : tasks) {
                relevantTasks.put(task.getTaskId().intValue(), task);
            }

            log.info("获取到相关推理任务: 数量={}, 任务ID={}",
                    relevantTasks.size(), relevantTasks.keySet());

        } catch (Exception e) {
            log.error("获取相关推理任务失败", e);
        }

        return relevantTasks;
    }

    /**
     * 获取线索关联的任务ID
     */
    private List<Integer> getAssociatedTaskIds(String fragmentId) {
        try {
            // 这里fragmentId可能来自不同向量类型，需要处理
            if (fragmentId.startsWith("clue_")) {
                // 这是线索ID，需要查找关联的任务
                String clueId = fragmentId.replace("clue_", "");
                try {
                    ClueFragment clueFragment = clueFragmentRepository.findById(Long.parseLong(clueId)).orElse(null);
                    if (clueFragment != null && clueFragment.getAssociatedTaskIds() != null) {
                        return clueFragment.getAssociatedTaskIds();
                    }
                } catch (NumberFormatException e) {
                    log.warn("无效的线索ID格式: {}", fragmentId);
                }
            } else if (fragmentId.startsWith("fragment_")) {
                // 这是片段ID，需要查找关联的任务
                String fragmentIdStr = fragmentId.replace("fragment_", "");
                try {
                    ClueFragment clueFragment = clueFragmentRepository.findById(Long.parseLong(fragmentIdStr)).orElse(null);
                    if (clueFragment != null && clueFragment.getAssociatedTaskIds() != null) {
                        return clueFragment.getAssociatedTaskIds();
                    }
                } catch (NumberFormatException e) {
                    log.warn("无效的片段ID格式: {}", fragmentId);
                }
            } else if (fragmentId.matches("\\d+")) {
                // 直接的片段ID
                try {
                    ClueFragment clueFragment = clueFragmentRepository.findById(Long.parseLong(fragmentId)).orElse(null);
                    if (clueFragment != null && clueFragment.getAssociatedTaskIds() != null) {
                        return clueFragment.getAssociatedTaskIds();
                    }
                } catch (NumberFormatException e) {
                    log.warn("无效的片段ID格式: {}", fragmentId);
                }
            }

            return Collections.emptyList();

        } catch (Exception e) {
            log.error("获取线索关联任务ID失败: fragmentId={}", fragmentId, e);
            return Collections.emptyList();
        }
    }
}