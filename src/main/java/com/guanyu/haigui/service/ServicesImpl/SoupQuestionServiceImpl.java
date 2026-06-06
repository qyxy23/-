package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.*;
import com.guanyu.haigui.Exception.AiResponseException;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.Info.SoupInfo;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.response.AIResponse;
import com.guanyu.haigui.pojo.result.ChatWithAIRoomRequest;
import com.guanyu.haigui.pojo.result.CompletedTasksResult;
import com.guanyu.haigui.pojo.result.ContextMatchResult;
import com.guanyu.haigui.pojo.result.UncompletedTasksResult;
import com.guanyu.haigui.pojo.vo.EndGameVO;
import com.guanyu.haigui.pojo.vo.RoomGetClueVO;
import com.guanyu.haigui.pojo.vo.RoomSoupQuestionVO;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.repository.*;
import com.guanyu.haigui.service.SoupQuestionService;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 海龟汤问题处理服务实现类
 * 基于向量匹配和AI判断的智能问答系统
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
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
    private final HaiGuiVoteSessionRepository haiGuiVoteSessionRepository;
    private final HaiGuiVoteRecordRepository haiGuiVoteRecordRepository;




    @Value("${haiqutang.ai.debug-mode:false}")
    private boolean debugMode;


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
        if(!(game.getStatus() == RoomStatus.ACTIVE|| game.getStatus() == RoomStatus.VOTING)){
            return RoomSoupQuestionVO.error("游戏未开始或已结束：ID=" + request.getRoomId());
        }
        GameSession session = gameSessionRepository.findById(game.getSessionId()).orElse(null);
        if(session == null|| session.getStatus() != GameSession.GameSessionStatus.ONGOING){
            return RoomSoupQuestionVO.error("游戏未开始或已结束：ID=" + request.getRoomId());
        }
        if(session.getRemainingQuestions()<=0){
            return RoomSoupQuestionVO.error("游戏次数已用完，可充值解锁更多机会");
        }
        ChatGameMember member = chatGameMemberRepository.findById(new ChatGameMemberId(BaseContext.getCurrentId(), request.getRoomId())).orElse(null);
        if (member == null){
            return RoomSoupQuestionVO.error("用户未加入房间：ID=" + BaseContext.getCurrentId());
        }
        String soupId = game.getHaiGuiSoup().getSoupId();
        if(!(game.getStatus() == RoomStatus.ACTIVE|| game.getStatus() == RoomStatus.VOTING)){
            return RoomSoupQuestionVO.error("游戏未开始：ID=" + request.getRoomId());
        }
        HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElse(null);
        if(soup == null){
            return RoomSoupQuestionVO.error("海龟汤不存在：ID=" + soupId);
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

        // 4. 获取房间当前状态
        Set<Long> triggeredFragmentIds = getCurrentTriggeredFragments(request.getRoomId());
        List<InferenceTask> allTasks = inferenceTaskRepository.findBySoupId(soupId);
        List<InferenceTask> incompleteTasks = allTasks.stream()
                .filter(task -> !haiGuiRoomProgressRepository.isTaskCompleted(request.getRoomId(), task.getTaskId()))
                .collect(Collectors.toList());

        // 5. 从向量检索结果提取本次新触发的线索
        Set<Long> newTriggeredFragments = extractNewTriggeredFragments(relevantClues, triggeredFragmentIds);

        // 6. 构建 AI 判题提示词（仅判题，不管理进度）
        String aiPrompt = buildAIPrompt(request.getQuestion(), relevantClues, soupInfo);

        // 7. 调用 AI 生成判断
        String aiResponse = generateAIResponse(aiPrompt);
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return RoomSoupQuestionVO.error("AI判断生成失败");
        }

        // 8. 解析 AI 响应（仅 ANSWER + REASON）
        AIResponse parsedResponse = parseAnswer(aiResponse);
        if (parsedResponse == null || parsedResponse.getAnswer() == null) {
            return RoomSoupQuestionVO.error("AI响应解析失败,请联系管理员");
        }

        // 9. 由 Java 根据向量触发 + 前置线索计算进度
        BigDecimal progress = updateRoomProgress(request.getRoomId(), newTriggeredFragments, incompleteTasks);

        // 9. 保存消息记录（使用枚举值）
        String answerText = parsedResponse.getAnswer().trim();
        QuestionWithAiAnswer answerEnum;

        // 建立中文到枚举的映射关系
        if ("是".equals(answerText) || "yes".equalsIgnoreCase(answerText)) {
            answerEnum = QuestionWithAiAnswer.YES;
        } else if ("不是".equals(answerText) || "no".equalsIgnoreCase(answerText)) {
            answerEnum = QuestionWithAiAnswer.NO;
        } else if ("是或不是".equals(answerText) || "部分正确".equals(answerText) || "无法确定".equals(answerText)) {
            answerEnum = QuestionWithAiAnswer.PARTIAL;  // 需要新增这个枚举值
        } else {
            // 保留原始异常处理作为兜底
            try {
                answerEnum = QuestionWithAiAnswer.valueOf(answerText.toUpperCase());
            } catch (IllegalArgumentException e) {
                answerEnum = QuestionWithAiAnswer.UNKNOWN;
            }
        }

        BigDecimal currentProgress = session.getCurrentProgress(); // 获取当前进度
        BigDecimal newProgress = currentProgress.add(progress); // 计算新进度
        session.updateProgress(newProgress); // 更新进度
        session.setRemainingQuestions(session.getRemainingQuestions() - 1);
        gameSessionRepository.save(session);

        HaiGuiChatMessageWithFragments haiGuiChatMessage = new HaiGuiChatMessageWithFragments();
        haiGuiChatMessage.setQuestionContent(request.getQuestion());
        haiGuiChatMessage.setAiAnswer(answerEnum);
        haiGuiChatMessage.setRoomId(request.getRoomId());
        haiGuiChatMessage.setUserId(userId);
        haiGuiChatMessage.setIsDeleted(false);
        haiGuiChatMessage.setTriggeredFragmentIds(new HashSet<>(newTriggeredFragments));
        haiGuiChatMessageRepository.save(haiGuiChatMessage);
        RoomSoupQuestionVO roomSoupQuestionVO = RoomSoupQuestionVO.success(
                request.getRoomId(),
                // haiGuiChatMessage.getId(),
                request.getQuestion(),
                parsedResponse.getAnswer(),
                progress.doubleValue(),
                session.getRemainingQuestions()
        );
        simpMessagingTemplate.convertAndSend("/topic/memberChange" + request.getRoomId(), roomSoupQuestionVO);
        if(session.getRemainingQuestions()==0){
            endGame(request.getRoomId());
        }
        return roomSoupQuestionVO;
    }

    @Override
    public RoomGetClueVO getClue(String roomId) {
        List<HaiGuiChatMessageWithFragments> messages = haiGuiChatMessageRepository.findAllByRoomId(roomId);

        ChatGame game = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(404, "房间不存在"));
        if(game.getSessionId()== null){
            return RoomGetClueVO.error("游戏未开始",game.getStatus());
        }
        GameSession session = gameSessionRepository.findById(game.getSessionId()).orElse(null);
        if (session == null) {
            return RoomGetClueVO.error("游戏会话不存在",game.getStatus());
        }
        HaiGuiSoup soup = haiGuiSoupRepository.findById(session.getSoupId()).orElse(null);
        if (soup == null) {
            return RoomGetClueVO.error("海龟汤不存在",game.getStatus());
        }
        RoomGetClueVO roomGetClueVO = new RoomGetClueVO();
        roomGetClueVO.setSoupSurface(soup.getSoupSurface());
        if(game.getStatus()==RoomStatus.FINISHED){
            roomGetClueVO.setMessage("游戏已结束");
            roomGetClueVO.setSoupBottom(soup.getSoupBottom());
        }else if(game.getStatus()==RoomStatus.VOTING){
            List<HaiGuiVoteSession> sessions = haiGuiVoteSessionRepository.findBySessionIdAndStatusOrderByCreatedAtDesc(
                    game.getSessionId(), HaiGuiVoteSession.VoteStatus.ONGOING);
            HaiGuiVoteSession currentSession = sessions.get(0); // 最新创建的会话

            // 3. 检查投票是否超时
            if (LocalDateTime.now().isAfter(currentSession.getEndTime())) {
                // 投票已超时，自动处理
                handleVoteTimeout(game,currentSession);
            }else{
                roomGetClueVO.setAgreedVotes(currentSession.getAgreedVotes());
                roomGetClueVO.setTotalVoters(currentSession.getTotalVoters());
                roomGetClueVO.setEndTime(currentSession.getEndTime());
                HaiGuiVoteRecord voteRecord = haiGuiVoteRecordRepository.findByVoteSessionIdAndUserId(
                        currentSession.getVoteSessionId(), BaseContext.getCurrentId());
                roomGetClueVO.setHasVoted(voteRecord != null);
                roomGetClueVO.setAgreed(voteRecord != null && voteRecord.getVoteOption() == HaiGuiVoteRecord.VoteOption.AGREE);
            }
        }
        roomGetClueVO.setRoomStatus(game.getStatus());
        if(game.getStatus()==RoomStatus.WAITING){
            return roomGetClueVO;
        }
        roomGetClueVO.setProgress(session.getCurrentProgress().doubleValue());
        roomGetClueVO.setRemainingQuestions(session.getRemainingQuestions());
        roomGetClueVO.setQuestion(ChatServicesImpl.getQuestions(messages));

        return roomGetClueVO;
    }

    private void handleVoteTimeout(ChatGame chatGame,HaiGuiVoteSession session) {
        // 标记为超时结束
        chatGame.setStatus(RoomStatus.ACTIVE);
        chatGame.setUpdateTime(LocalDateTime.now());
        chatGameRepository.save(chatGame);
        session.setStatus(HaiGuiVoteSession.VoteStatus.FAILED);
        session.setUpdatedAt(LocalDateTime.now());
        haiGuiVoteSessionRepository.save(session);
    }


    // 结束游戏
    public EndGameVO endGame(String roomId) {
        ChatGame chatGame = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(404, "房间不存在"));
        HaiGuiSoup soup = haiGuiSoupRepository.findById(chatGame.getHaiGuiSoup().getSoupId())
                .orElseThrow(() -> new BusinessException(404, "该海龟汤不存在"));
        GameSession gameSession = gameSessionRepository.findById(chatGame.getSessionId())
                .orElseThrow(() -> new BusinessException(404, "游戏会话不存在"));

        // 1. 获取所有任务进度
        List<HaiGuiRoomProgress> progressList = haiGuiRoomProgressRepository.findByRoomId(roomId);
        Map<Long, HaiGuiRoomProgress> progressMap = progressList.stream()
                .collect(Collectors.toMap(HaiGuiRoomProgress::getTaskId, Function.identity()));

        // 2. 获取所有推理任务
        List<InferenceTask> allTasks = inferenceTaskRepository.findBySoupId(soup.getSoupId());

        // 3. 使用 BigDecimal 计算任务完成情况和得分
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal completedWeight = BigDecimal.ZERO;
        List<CompletedTasksResult> completedTasks = new ArrayList<>();
        List<UncompletedTasksResult> uncompletedTasks = new ArrayList<>();

        for (InferenceTask task : allTasks) {
            HaiGuiRoomProgress progress = progressMap.get(task.getTaskId());
            boolean isCompleted = progress != null && progress.getCompleted();

            CompletedTasksResult result = new CompletedTasksResult();
            result.setTaskId(task.getTaskId());
            result.setTaskName(task.getTaskName());
            result.setDescription(task.getTaskDescription());
            result.setCompletionTime(progress != null ? progress.getCompletionTime() : null);

            // 使用 BigDecimal 处理权重
            BigDecimal taskWeight = task.getProgressWeight();

            if (isCompleted) {
                completedWeight = completedWeight.add(taskWeight);
                completedTasks.add(result);
            } else {
                UncompletedTasksResult CurrentResult = new UncompletedTasksResult();
                CurrentResult.setTaskId(task.getTaskId());
                CurrentResult.setTaskName(task.getTaskName());
                CurrentResult.setDescription(task.getTaskDescription());
                uncompletedTasks.add(CurrentResult);
            }
            totalWeight = totalWeight.add(taskWeight);
        }

        // 4. 计算最终得分 (0-100) - 使用 BigDecimal 精确计算
        BigDecimal completionPercentage;
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            // (completedWeight / totalWeight) * 100
            completionPercentage = completedWeight.divide(totalWeight, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP); // 保留两位小数
        } else {
            completionPercentage = BigDecimal.ZERO;
        }

        // 计算整数得分（四舍五入）
        int finalScore = completionPercentage.setScale(0, RoundingMode.HALF_UP).intValueExact();

        // 5. 更新游戏会话状态 - 使用 BigDecimal 版本
        gameSession.setStatus(GameSession.GameSessionStatus.COMPLETED);
        gameSession.setEndTime(LocalDateTime.now());
        gameSession.setCurrentProgress(completionPercentage); // 使用 BigDecimal
        gameSession.setScore(BigDecimal.valueOf(finalScore)); // 使用 BigDecimal
        gameSessionRepository.save(gameSession);

        // 6. 更新海龟汤游玩次数
        soup.setPlayCount(soup.getPlayCount() + 1);
        haiGuiSoupRepository.save(soup);

        // 7. 更新房间状态
        chatGame.setStatus(RoomStatus.FINISHED);
        chatGame.setEndTime(LocalDateTime.now());
        chatGameRepository.save(chatGame);

        List<ChatGameMember> chatGameMember = chatGameMemberRepository.findByIdRoomId(roomId);
        chatGameMember.forEach(member -> member.setStatus(MemberStatus.ONLINE));
        chatGameMemberRepository.saveAll(chatGameMember);

        // 8. 构建返回对象 - 转换为 double 和 int 用于 VO
        EndGameVO endGameVO = new EndGameVO();
        endGameVO.setRoomId(roomId);
        endGameVO.setSoupBottom(soup.getSoupBottom());
        endGameVO.setStatus(RoomStatus.FINISHED);
        endGameVO.setChatType(MessageChatType.GAME_END);
        endGameVO.setCurrentProgress(completionPercentage.doubleValue()); // BigDecimal 转 double
        endGameVO.setFinalScore(finalScore); // 已经是 int
        endGameVO.setCompletedTasks(completedTasks);
        endGameVO.setUncompletedTasks(uncompletedTasks);
        endGameVO.setTotalTasks(allTasks.size());

        // 9. 发送结果给客户端
        simpMessagingTemplate.convertAndSend("/topic/memberChange" + roomId, endGameVO);

        return endGameVO;
    }


    protected BigDecimal updateRoomProgress(String roomId, Set<Long> newFragments,
                                          List<InferenceTask> incompleteTasks) {
        if (newFragments == null || newFragments.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map<Long, Set<Long>> taskFragmentsMap = getCurrentTaskFragments(roomId, incompleteTasks);
        BigDecimal progress = BigDecimal.ZERO;

        for (InferenceTask task : incompleteTasks) {
            Long taskId = task.getTaskId();
            List<Long> prerequisiteIds = task.getPrerequisiteFragmentIds();

            Set<Long> currentFragments = taskFragmentsMap.getOrDefault(taskId, new HashSet<>());
            Set<Long> updatedFragments = new HashSet<>(currentFragments);

            Set<Long> relevantNewFragments = new HashSet<>(newFragments);
            relevantNewFragments.retainAll(prerequisiteIds);
            updatedFragments.addAll(relevantNewFragments);

            boolean allPrerequisitesMet = !prerequisiteIds.isEmpty()
                    && updatedFragments.containsAll(prerequisiteIds);

            if (allPrerequisitesMet) {
                log.info("任务完成：ID={}，前置线索={}，已触发线索={}",
                        taskId, prerequisiteIds, updatedFragments);
                progress = progress.add(task.getProgressWeight());
                haiGuiRoomProgressRepository.updateTaskStatus(
                        roomId, taskId, true, updatedFragments, LocalDateTime.now());
            } else if (!relevantNewFragments.isEmpty()) {
                log.info("更新任务线索：ID={}，新增线索={}，总线索={}",
                        taskId, relevantNewFragments, updatedFragments);
                haiGuiRoomProgressRepository.updateTaskFragments(
                        roomId, taskId, updatedFragments, LocalDateTime.now());
            }
        }

        return progress;
    }

    private Set<Long> extractNewTriggeredFragments(Map<String, List<ContextMatchResult>> relevantClues,
                                                   Set<Long> alreadyTriggered) {
        Set<Long> newFragments = new HashSet<>();
        for (List<ContextMatchResult> results : relevantClues.values()) {
            for (ContextMatchResult result : results) {
                try {
                    Long fragmentId = Long.parseLong(result.getId());
                    if (!alreadyTriggered.contains(fragmentId)) {
                        newFragments.add(fragmentId);
                    }
                } catch (NumberFormatException e) {
                    log.warn("无法解析片段ID: {}", result.getId());
                }
            }
        }
        return newFragments;
    }

    // 获取每个任务独立的已触发线索
    private Map<Long, Set<Long>> getCurrentTaskFragments(String roomId, List<InferenceTask> tasks) {
        List<Long> taskIds = tasks.stream()
                .map(InferenceTask::getTaskId)
                .collect(Collectors.toList());

        Map<Long, Set<Long>> taskFragmentsMap = new HashMap<>();

        if (!taskIds.isEmpty()) {
            // 调用修改后的仓库方法
            List<Object[]> results = haiGuiRoomProgressRepository.findTaskFragmentsRaw(roomId, taskIds);


            for (Object[] result : results) {
                Long taskId = (Long) result[0];
                Object fragmentObj = result[1];

                // 安全地将 Object 转换为 Set<Long>
                Set<Long> fragments;
                if (fragmentObj instanceof Set<?>) {
                    try {
                        fragments = ((Set<?>) fragmentObj).stream()
                                .map(Long.class::cast)
                                .collect(Collectors.toSet());
                    } catch (ClassCastException e) {
                        log.warn("转换任务线索ID失败，taskId={}", taskId, e);
                        fragments = new HashSet<>();
                    }
                } else {
                    fragments = new HashSet<>();
                }

                taskFragmentsMap.put(taskId, fragments);
            }

        }

        // 为所有任务添加条目（即使没有记录）
        for (InferenceTask task : tasks) {
            taskFragmentsMap.putIfAbsent(task.getTaskId(), new HashSet<>());
        }

        return taskFragmentsMap;
    }

    // 辅助方法：获取当前已触发的线索
    private Set<Long> getCurrentTriggeredFragments(String roomId) {
        // 合并所有记录的线索
        List<HaiGuiRoomProgress> allProgress = haiGuiRoomProgressRepository.findByRoomId(roomId);
        Set<Long> allFragments = new HashSet<>();
        for (HaiGuiRoomProgress progress : allProgress) {
            allFragments.addAll(progress.getTriggeredFragmentIds());
        }
        return allFragments;
    }




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

    public String buildAIPrompt(String question,
                              Map<String, List<ContextMatchResult>> relevantClues,
                              SoupInfo soupInfo) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("=== 海龟汤背景 ===\n");
        prompt.append(String.format("汤面：%s\n", soupInfo.getSoupSurface()));
        prompt.append(String.format("汤底：%s\n", soupInfo.getSoupBottom()));
        if (soupInfo.getAiJudgeRules() != null && !soupInfo.getAiJudgeRules().isBlank()) {
            prompt.append(String.format("判题规则：%s\n", soupInfo.getAiJudgeRules()));
        }
        prompt.append("\n");

        List<ContextMatchResult> matchedClues = relevantClues.getOrDefault("CLUE_FRAGMENT", List.of());
        if (!matchedClues.isEmpty()) {
            prompt.append("=== 与本问题语义相关的线索（仅供参考，勿主动透露） ===\n");
            for (ContextMatchResult clue : matchedClues) {
                prompt.append(String.format("- %s\n", clue.getContent()));
            }
            prompt.append("\n");
        }

        prompt.append("=== 玩家问题 ===\n");
        prompt.append(String.format("%s\n\n", question));

        prompt.append("=== 回答要求 ===\n");
        prompt.append("请严格按以下格式返回：\n");
        prompt.append("ANSWER: [是/不是/是或不是/不重要]\n");
        prompt.append("REASON: [简短判断理由]\n");

        return prompt.toString();
    }

    /**
     * 获取模拟AI响应数据（调试模式使用）
     * @return 模拟的AI响应JSON字符串
     */
    private String getMockAiResponse() {
        try {
            ClassPathResource resource = new ClassPathResource("temp/aiResponse2.txt");
            if (!resource.exists()) {
                log.error("模拟AI响应文件不存在: temp/aiResponse.txt");
                throw new AiResponseException("模拟AI响应文件不存在");
            }

            byte[] data = FileCopyUtils.copyToByteArray(resource.getInputStream());
            String mockResponse = new String(data, StandardCharsets.UTF_8);

            log.info("成功读取模拟AI响应，文件大小: {} 字节", mockResponse.length());
            return mockResponse;

        } catch (IOException e) {
            log.error("读取模拟AI响应文件失败", e);
            throw new AiResponseException("模拟AI响应文件不存在");
        }
    }

    @Override
    public String generateAIResponse(String prompt) {
        if (debugMode) {
            return getMockAiResponse();
        }
        try {
            log.debug("调用AI生成判断，提示词长度: {}", prompt.length());

            // 更新系统提示词（强调新规则）
            String systemPrompt = "你是海龟汤游戏的AI主持人，根据汤底和判题规则回答玩家的封闭问题。"
                    + "只回答「是」「不是」「是或不是」「不重要」之一，不要透露玩家未问到的信息。";

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

            for (String line : aiResponse.split("\n")) {
                if (line.startsWith("ANSWER:")) {
                    response.setAnswer(line.replace("ANSWER:", "").trim());
                } else if (line.startsWith("REASON:")) {
                    response.setReason(line.replace("REASON:", "").trim());
                }
            }

            if (response.getAnswer() == null) {
                log.warn("AI响应缺少 ANSWER 字段: {}", aiResponse);
                return fallbackParse(aiResponse);
            }

            return response;
        } catch (Exception e) {
            log.error("解析AI回答失败", e);
            return fallbackParse(aiResponse);
        }
    }

    private AIResponse fallbackParse(String aiResponse) {
        AIResponse response = new AIResponse();
        response.setRawResponse(aiResponse);

        if (aiResponse.contains("ANSWER:")) {
            String answerLine = aiResponse.lines()
                    .filter(l -> l.startsWith("ANSWER:"))
                    .findFirst().orElse("");
            response.setAnswer(answerLine.replace("ANSWER:", "").trim());
        }

        return response;
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
                    soup.getAiJudgeRules(),
                    null
            );

        } catch (Exception e) {
            log.error("获取海龟汤信息失败: soupId={}", soupId, e);
            return null;
        }
    }


}