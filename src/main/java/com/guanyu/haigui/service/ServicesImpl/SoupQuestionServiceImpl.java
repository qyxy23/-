package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.QuestionWithAiAnswer;
import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.Enum.VectorType;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.Info.SoupInfo;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.response.AIResponse;
import com.guanyu.haigui.pojo.result.ChatWithAIRoomRequest;
import com.guanyu.haigui.pojo.result.ContextMatchResult;
import com.guanyu.haigui.pojo.vo.RoomSoupQuestionVO;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.repository.*;
import com.guanyu.haigui.service.SoupQuestionService;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    private final RedisStackClient redisClient;
    private final AIManager aiManager;
    private final ChatGameRepository chatGameRepository;
    private final ChatGameMemberRepository chatGameMemberRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final GameSessionRepository gameSessionRepository;
    private final UserInfoRepository userRepository;
    private final HaiGuiChatMessageRepository haiGuiChatMessageRepository;
    private final HaiGuiRoomProgressRepository haiGuiRoomProgressRepository;




    @Override
    public RoomSoupQuestionVO RoomProcessSoupQuestion(ChatWithAIRoomRequest request) {
        Long userId = BaseContext.getCurrentId();
        UserInfo user = userRepository.findById(userId).orElse(null);
        if (user == null){
            return RoomSoupQuestionVO.error("用户不存在：ID=" + userId);
        }
        ChatGame game = chatGameRepository.findById(request.getRoomId()).orElse(null);
        if(game == null){
            return RoomSoupQuestionVO.error("房间不存在：ID=" + request.getRoomId());
        }
        ChatGameMember member = chatGameMemberRepository.findById(new ChatGameMemberId(BaseContext.getCurrentId(), request.getRoomId())).orElse(null);
        if (member == null){
            return RoomSoupQuestionVO.error("用户未加入游戏：ID=" + BaseContext.getCurrentId());
        }
        String soupId = game.getHaiGuiSoup().getSoupId();
        if(game.getStatus() != RoomStatus.ACTIVE){
            return RoomSoupQuestionVO.error("游戏未开始：ID=" + request.getRoomId());
        }
        HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElse(null);
        if(soup == null){
            return RoomSoupQuestionVO.error("海龟汤不存在：ID=" + soupId);
        }
        GameSession session = gameSessionRepository.findById(game.getSessionId()).orElse(null);
        if(session == null){
            return RoomSoupQuestionVO.error("游戏未开始：ID=" + request.getRoomId());
        }
        // 2. 向量化问题
        List<Float> questionVector = vectorizeQuestion(request.getQuestion());
        if (questionVector.isEmpty()) {
            return RoomSoupQuestionVO.error("问题向量化失败");
        }
        Map<String, List<ContextMatchResult>> relevantClues = searchRelevantClues(
                soupId,
                questionVector,
                5,//TODO:后期要修改成根据数据库的配置进行修改
                0.3,//匹配相似度
                request.getQuestion()
        );

        SoupInfo soupInfo = getSoupInfo(soupId);

        // 4. 获取房间当前状态（新增）
        List<Long> triggeredFragmentIds = haiGuiRoomProgressRepository.findTriggeredFragmentIds(request.getRoomId());
        List<InferenceTask> allTasks = inferenceTaskRepository.findBySoupId(soupId);
        List<InferenceTask> incompleteTasks = allTasks.stream()
                .filter(task -> !haiGuiRoomProgressRepository.isTaskCompleted(request.getRoomId(), task.getTaskId()))
                .collect(Collectors.toList());

        // 5. 构建新的AI提示词（使用新格式）
        String aiPrompt = buildAIPrompt(
                request.getRoomId(),
                request.getQuestion(),
                relevantClues,
                soupInfo,
                triggeredFragmentIds,
                incompleteTasks
        );

        // 6. 调用AI生成判断（使用新系统提示词）
        String aiResponse = generateAIResponse(aiPrompt);
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return RoomSoupQuestionVO.error("AI判断生成失败");
        }

        // 7. 解析新的AI响应格式
        AIResponse parsedResponse = parseAnswer(aiResponse);
        if (parsedResponse == null || parsedResponse.getAnswer() == null) {
            return RoomSoupQuestionVO.error("AI响应解析失败");
        }

        // 8. 更新房间进度（新增）
        updateRoomProgress(request.getRoomId(), parsedResponse);

        // 9. 保存消息记录（使用枚举值）
        QuestionWithAiAnswer answerEnum;
        try {
            answerEnum = QuestionWithAiAnswer.valueOf(parsedResponse.getAnswer());
        } catch (IllegalArgumentException e) {
            // 处理可能的枚举转换错误
            answerEnum = QuestionWithAiAnswer.UNKNOWN;
        }

        HaiGuiChatMessage haiGuiChatMessage = new HaiGuiChatMessage();
        haiGuiChatMessage.setMessageId(UUID.randomUUID().toString());
        haiGuiChatMessage.setQuestionContent(request.getQuestion());
        haiGuiChatMessage.setAiAnswer(answerEnum);
        haiGuiChatMessage.setRoomId(request.getRoomId());
        haiGuiChatMessage.setUserId(userId);
        haiGuiChatMessage.setIsDeleted(false);
        haiGuiChatMessageRepository.save(haiGuiChatMessage);
        RoomSoupQuestionVO roomSoupQuestionVO = RoomSoupQuestionVO.success(
                request.getRoomId(),
                request.getQuestion(),
                parsedResponse.getAnswer()
        );
        simpMessagingTemplate.convertAndSend("/topic/chat/" + request.getRoomId(), roomSoupQuestionVO);
        return roomSoupQuestionVO;
    }

    private void updateRoomProgress(String roomId, AIResponse parsedResponse) {
        // 获取当前已触发的线索
        List<Long> currentFragments = haiGuiRoomProgressRepository.findTriggeredFragmentIds(roomId);
        Set<Long> allFragments = new HashSet<>(currentFragments);

        // 添加新触发的线索
        if (parsedResponse.getNewTriggeredFragments() != null) {
            allFragments.addAll(parsedResponse.getNewTriggeredFragments());
        }

        // 更新每个新完成的任务
        if (parsedResponse.getCompletedTasks() != null) {
            for (Long taskId : parsedResponse.getCompletedTasks()) {
                haiGuiRoomProgressRepository.updateTaskStatus(
                        roomId,
                        taskId,
                        true,
                        new ArrayList<>(allFragments),
                        LocalDateTime.now()
                );
            }
        }

        // 更新已触发线索（即使没有任务完成）
        if (parsedResponse.getNewTriggeredFragments() != null &&
                !parsedResponse.getNewTriggeredFragments().isEmpty()) {
            haiGuiRoomProgressRepository.addTriggeredFragments(
                    roomId,
                    new ArrayList<>(allFragments)
            );
        }
    }

    // @Override
    // @Transactional
    // public String processSoupQuestion(SoupQuestionRequest request) {
    //
    //     log.info("开始处理海龟汤问题: soupId={}, question={}",
    //             request.getSoupId(),
    //             request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));
    //
    //     // 2. 向量化问题
    //     List<Float> questionVector = vectorizeQuestion(request.getQuestion());
    //
    //
    //     log.info("问题向量化成功，维度: {}", questionVector.size());
    //
    //     // 3. 搜索相关线索
    //     Map<String, List<ContextMatchResult>> relevantClues = searchRelevantClues(
    //             request.getSoupId(),
    //             questionVector,
    //             request.getTopK(),
    //             request.getMinSimilarity(),
    //             request.getQuestion()
    //     );
    //     log.info("搜索相关线索成功，数量: {}", relevantClues.size());
    //
    //     // 4. 获取海龟汤信息
    //     SoupInfo soupInfo = getSoupInfo(request.getSoupId());
    //
    //     // 5. 构建AI提示词
    //     String aiPrompt = buildAIPrompt(
    //             request.getRoomId(),
    //             request.getQuestion(),
    //             relevantClues,
    //             soupInfo,
    //             triggeredFragmentIds,
    //             incompleteTasks
    //     );
    //
    //     log.info("构建AI提示词成功: {}", aiPrompt);
    //
    //     return aiPrompt;
    // }
    //
    // @Override
    // public boolean validateRequest(SoupQuestionRequest request) {
    //     if (request == null) {
    //         log.warn("请求对象为空");
    //         return false;
    //     }
    //
    //     if (request.getSoupId() == null || request.getSoupId().trim().isEmpty()) {
    //         log.warn("海龟汤ID为空");
    //         return false;
    //     }
    //
    //     if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
    //         log.warn("问题内容为空");
    //         return false;
    //     }
    //
    //     if (request.getTopK() != null && (request.getTopK() <= 0 || request.getTopK() > 20)) {
    //         log.warn("topK参数超出范围: {}", request.getTopK());
    //         return false;
    //     }
    //
    //     if (request.getMinSimilarity() != null && (request.getMinSimilarity() < 0 || request.getMinSimilarity() > 1)) {
    //         log.warn("minSimilarity参数超出范围: {}", request.getMinSimilarity());
    //         return false;
    //     }
    //
    //     return true;
    // }

    @Override
    public List<Float> vectorizeQuestion(String question) {
        try {
            log.debug("开始向量化问题: {}", question.substring(0, Math.min(30, question.length())));

            // 使用BGE模型向量化
            SingleEncodeResponse response = BgeVectorClientUtil.encodeSingle(question);
            if (response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
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
    public Map<String, List<ContextMatchResult>> searchRelevantClues(String soupId,
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
            Map<String, List<ContextMatchResult>> results = new HashMap<>();
            List<ContextMatchResult> fragmentMatchResults = new ArrayList<>();

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
                        ContextMatchResult result = new ContextMatchResult(
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

    public String buildAIPrompt(
            String roomId,
            String question,
            Map<String, List<ContextMatchResult>> relevantClues,
            SoupInfo soupInfo,
            List<Long> triggeredFragmentIds,       // 新增：已触发的线索ID列表
            List<InferenceTask> incompleteTasks     // 新增：未完成的任务列表
    ) {
        StringBuilder prompt = new StringBuilder();

        // 1. 海龟汤背景信息（保持不变）
        prompt.append("=== 海龟汤背景 ===\n");
        prompt.append(String.format("标题：%s\n", soupInfo.getSoupTitle()));
        prompt.append(String.format("汤面：%s\n", soupInfo.getSoupSurface()));
        prompt.append(String.format("汤底：%s\n", soupInfo.getSoupBottom()));
        prompt.append(String.format("主持人手册：%s\n\n", soupInfo.getHostManual()));

        // 2. 房间当前状态（新增核心部分）
        prompt.append("=== 房间当前状态 ===\n");
        prompt.append(String.format("房间ID: %s\n", roomId));
        prompt.append(String.format("海龟汤ID: %s\n", soupInfo.getSoupId()));
        prompt.append(String.format("已触发线索ID列表: %s\n",
                triggeredFragmentIds.toString().replace(" ", "")));

        prompt.append("未完成任务列表:\n");
        if (incompleteTasks.isEmpty()) {
            prompt.append("无\n");
        } else {
            for (InferenceTask task : incompleteTasks) {
                prompt.append(String.format(
                        "- 任务ID:%d, 前置线索ID:%s, 描述:%s\n",
                        task.getTaskId(),
                        task.getPrerequisiteFragmentIds().toString().replace(" ", ""),
                        task.getTaskDescription()
                ));
            }
        }
        prompt.append("\n");

        // 3. 玩家新问题上下文（新增）
        prompt.append("=== 玩家新问题上下文 ===\n");
        prompt.append(String.format("玩家问题: %s\n", question));

        // 提取新问题匹配的线索ID（从relevantClues中获取）
        List<String> newMatchedFragmentIds = new ArrayList<>();
        for (List<ContextMatchResult> results : relevantClues.values()) {
            for (ContextMatchResult result : results) {
                newMatchedFragmentIds.add(result.getId());
            }
        }
        prompt.append(String.format("新问题匹配的线索ID: %s\n\n",
                newMatchedFragmentIds.toString().replace(" ", "")));

        // 4. 任务完成规则（新增）
        prompt.append("=== 任务完成规则 ===\n");
        prompt.append("1. 线索触发：任务完成当且仅当「任务的所有前置线索ID都在已触发线索列表中」（含本次新触发的线索）\n");
        prompt.append("2. 只触发一次：已完成的任务不再更新\n");
        prompt.append("3. 去重：已存在的线索ID不重复添加到已触发列表\n\n");

        // 5. 输出要求（修改格式）
        prompt.append("=== 回答要求 ===\n");
        prompt.append("请严格按照以下格式返回结果：\n");
        prompt.append("ANSWER: [是/不是/是或不是/不重要]\n");
        prompt.append("NEW_TRIGGERED_FRAGMENTS: [新触发的线索ID列表]\n");
        prompt.append("COMPLETED_TASKS: [本次新完成的任务ID列表]\n");
        prompt.append("UPDATED_TRIGGERED_IDS: [更新后的已触发线索ID列表]\n");
        prompt.append("REASON: [判断理由]\n");

        return prompt.toString();
    }

    @Override
    public String generateAIResponse(String prompt) {
        try {
            log.debug("调用AI生成判断，提示词长度: {}", prompt.length());

            // 更新系统提示词（强调新规则）
            String systemPrompt = "你是海龟汤游戏的AI主持人，负责根据房间已触发线索和新线索，判断任务是否完成（每个任务只触发一次）。"
                    + "严格遵循任务完成规则：任务完成当且仅当所有前置线索ID都在已触发线索列表中（含本次新触发的线索）。";

            String response = aiManager.doChat(systemPrompt, prompt);

            if (response == null || response.trim().isEmpty()) {
                log.error("AI返回空响应");
                return null;
            }

            return response;
        } catch (Exception e) {
            log.error("调用AI生成判断失败", e);
            return null;
        }
    }

    @Override
    public AIResponse parseAnswer(String aiResponse) {
        try {
            log.debug("解析AI回答: {}", aiResponse.substring(0, Math.min(100, aiResponse.length())));

            AIResponse response = new AIResponse();
            response.setRawResponse(aiResponse);

            // 解析每一行
            for (String line : aiResponse.split("\n")) {
                if (line.startsWith("ANSWER:")) {
                    response.setAnswer(line.replace("ANSWER:", "").trim());
                }
                else if (line.startsWith("NEW_TRIGGERED_FRAGMENTS:")) {
                    String json = line.replace("NEW_TRIGGERED_FRAGMENTS:", "").trim();
                    response.setNewTriggeredFragments(parseJsonArray(json));
                }
                else if (line.startsWith("COMPLETED_TASKS:")) {
                    String json = line.replace("COMPLETED_TASKS:", "").trim();
                    response.setCompletedTasks(parseJsonArray(json));
                }
                else if (line.startsWith("UPDATED_TRIGGERED_IDS:")) {
                    String json = line.replace("UPDATED_TRIGGERED_IDS:", "").trim();
                    response.setUpdatedTriggeredIds(parseJsonArray(json));
                }
                else if (line.startsWith("REASON:")) {
                    response.setReason(line.replace("REASON:", "").trim());
                }
            }

            // 验证必要字段
            if (response.getAnswer() == null || response.getNewTriggeredFragments() == null) {
                log.warn("AI响应缺少必要字段: {}", aiResponse);
                return fallbackParse(aiResponse);
            }

            return response;
        } catch (Exception e) {
            log.error("解析AI回答失败", e);
            return fallbackParse(aiResponse);
        }
    }

    // 辅助方法：解析JSON数组字符串
    private List<Long> parseJsonArray(String json) {
        try {
            // 简单解析 [1,2,3] 格式
            String clean = json.replace("[", "").replace("]", "").replace(" ", "");
            if (clean.isEmpty()) return Collections.emptyList();

            return Arrays.stream(clean.split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("解析数组失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    // 备用解析方法（兼容旧格式）
    private AIResponse fallbackParse(String aiResponse) {
        AIResponse response = new AIResponse();
        response.setRawResponse(aiResponse);

        // 旧格式解析逻辑（保持兼容）
        if (aiResponse.contains("ANSWER:")) {
            String answerLine = aiResponse.lines()
                    .filter(l -> l.startsWith("ANSWER:"))
                    .findFirst().orElse("");
            response.setAnswer(answerLine.replace("ANSWER:", "").trim());
        }

        // 尝试提取AFFECTED_TASKS
        if (aiResponse.contains("AFFECTED_TASKS:")) {
            String tasksLine = aiResponse.lines()
                    .filter(l -> l.startsWith("AFFECTED_TASKS:"))
                    .findFirst().orElse("");
            String tasks = tasksLine.replace("AFFECTED_TASKS:", "").trim();
            response.setCompletedTasks(parseSimpleArray(tasks));
        }

        return response;
    }

    // 简单数组解析（非JSON格式）
    private List<Long> parseSimpleArray(String arrayStr) {
        try {
            return Arrays.stream(arrayStr.replace("[", "").replace("]", "").split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
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


}