package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.lang.UUID;
import com.guanyu.haigui.Enum.ChatContextType;
import com.guanyu.haigui.Enum.ChatMessageRole;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.Exception.NoBeginRequest;
import com.guanyu.haigui.constant.StatusConstant;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.mapper.AiChatSessionMapper;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.result.CompletedCluesResult;
import com.guanyu.haigui.pojo.result.CompletedTasksResult;
import com.guanyu.haigui.pojo.result.UncompletedTasksResult;
import com.guanyu.haigui.pojo.vo.ChatListVO;
import com.guanyu.haigui.pojo.vo.FirstChatVo;
import com.guanyu.haigui.pojo.vo.RoomGetClueVO;
import com.guanyu.haigui.pojo.vo.getAIChatListDetailVO;
import com.guanyu.haigui.repository.*;
import com.guanyu.haigui.service.ChatService;
import com.guanyu.haigui.utils.RedisServiceUtil;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import io.micrometer.common.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class ChatServicesImpl implements ChatService {
    private final Map<String, List<ChatMessage>> globalMessageMap = new ConcurrentHashMap<>(); // 缓存会话消息
    private final AiChatSessionRepository aiChatSessionRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private AiChatSessionMapper aiChatMapper;
    private AIManager aiManager;
    private RedisServiceUtil redisServiceUtil;
    private final ChatGameMemberRepository chatGameMemberRepository;
    private final ChatGameRepository chatGameRepository;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final GameSessionRepository gameSessionRepository;
    private final HaiGuiRoomProgressRepository haiGuiRoomProgressRepository;
    private final HaiGuiChatMessageRepository haiGuiChatMessageRepository;
    private final InferenceTaskRepository inferenceTaskRepository;
    private final ClueFragmentRepository clueFragmentRepository;




    /**
     * 检测标题是否带书名号（《》），若未带则自动补全
     *
     * @param title 原始标题
     * @return 带书名号的标题（若原始无，则补全；若有，则保持原样）
     */
    public static String ensureBookTitle(String title) {
        if (StringUtils.isBlank(title)) {
            return title;
        }
        // 去除前后空白
        String trimmedTitle = title.trim();
        // 检查是否以《开头且以》结尾
        if (trimmedTitle.startsWith("《") && trimmedTitle.endsWith("》")) {
            return trimmedTitle;
        }
        // 未带书名号：补全《和》
        return "《" + trimmedTitle + "》";
    }

    @Override
    @Transactional
    public FirstChatVo doFirstChatWithAi(String message) {
        String roomId = UUID.randomUUID().toString();
        // 1. 获取当前用户ID BaseContext
        Long userId = BaseContext.getCurrentId();
        List<ChatMessage> messages = new ArrayList<>(); // 统一用官方ChatMessage缓存

        // 2. 校验会话合法性：未开始且消息不含“开始”，拒绝请求
        if (!message.contains("开始")) {
            throw new NoBeginRequest(StatusConstant.NoBeginRequest);
        }

        // 3.1 插入会话记录（关联当前用户）
        AiChatSession session = AiChatSession.builder()
                .sessionId(roomId).userId(userId).isDeleted(false).contextId(String.valueOf(userId))
                .contextType(ChatContextType.PRIVATE_CHAT).title("新对话").build();

        try {
            aiChatSessionRepository.save(session);
        } catch (RuntimeException e) {
            // 打印详细错误日志（包含sessionId、userId和原异常栈）
            log.error("插入会话失败 | sessionId: {}, userId: {}", roomId, userId, e);
            // 抛出包含原异常信息的运行时异常（方便上层捕获）
            throw new RuntimeException("会话插入失败：" + e.getMessage(), e);
        }

        // 3.2 插入系统消息（官方ChatMessage）
        AiChatMessage systemMsg = AiChatMessage.builder().chatSession(session)
                .sendTime(LocalDateTime.now()).isRead(0).role(ChatMessageRole.SYSTEM)
                .content(StatusConstant.SystemFirstPrompt).build();
        // 3.3 初始化缓存：存系统消息
        messages.add(convertSingleAiMessage(systemMsg));
        redisServiceUtil.updateOnlineRooms(roomId); // 标记会话已开始

        // 4. 插入用户消息（官方ChatMessage）
        AiChatMessage userMsg = AiChatMessage.builder().chatSession(session)
                .sendTime(LocalDateTime.now()).isRead(0).senderId(userId)
                .role(ChatMessageRole.USER).content(message).build();
        // userMsg.setRole(ChatMessageRole.USER); // 用户消息角色
        // userMsg.setContent(message);
        messages.add(convertSingleAiMessage(userMsg));

        // 5. 调用AI生成回复
        String answer = aiManager.doChat(messages);

        // 使用更宽松的正则表达式，允许换行和空格
        Pattern pattern = Pattern.compile("汤面[：:]\\s*(.*?)\\s*标题[：:]\\s*(.*)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(answer.trim());

        if (!matcher.find()) {
            throw new RuntimeException("无法解析AI回复");
        }

        String soupContent = matcher.group(1).trim();
        String titleContent = matcher.group(2).trim();
        System.out.println("汤面内容: \"" + soupContent + "\"");
        System.out.println("标题内容: \"" + titleContent + "\"");
        titleContent = ensureBookTitle(titleContent);
        System.out.println("titleContent = " + titleContent);

        systemMsg.setContent(StatusConstant.SystemPrompt);
        aiChatMessageRepository.save(systemMsg); // 插入消息
        aiChatMessageRepository.save(userMsg); // 插入消息（合并原insertUserMsg）
        session.setTitle(titleContent);
        // session.setUpdateTime(LocalDateTime.now()); // 确保更新时间不为null
        aiChatSessionRepository.updateBySessionId(session);

        // 6. 插入AI回复（官方ChatMessage，角色为ASSISTANT）
        AiChatMessage assistantMsg = AiChatMessage.builder().chatSession(session)
                .sendTime(LocalDateTime.now()).isRead(0).role(ChatMessageRole.ASSISTANT)
                .content(soupContent).build();
        // assistantMsg.setRole(ChatMessageRole.ASSISTANT); // AI回复角色
        // assistantMsg.setContent(answer);
        aiChatMessageRepository.save(assistantMsg);// 插入消息（合并原insertAIMsg）
        messages.add(convertSingleAiMessage(assistantMsg));
        // 更新缓存
        globalMessageMap.put(roomId, messages);

        FirstChatVo firstChatVo = new FirstChatVo();
        firstChatVo.setRoomId(roomId);
        firstChatVo.setTitle(titleContent);
        firstChatVo.setMessage(soupContent);
        return firstChatVo;
    }

    /**
     * 非首次会话聊天
     *
     * @param roomId  房间id
     * @param message 问题
     */
    @Transactional
    public String chatWithAI(String roomId, String message) {
        // 1. 获取当前用户ID BaseContext
        Long userId = BaseContext.getCurrentId();
        List<ChatMessage> messages; // 统一用官方ChatMessage缓存

        // 3.4 非首次会话：从缓存读取历史消息
        messages = globalMessageMap.get(roomId);
        if (messages == null) {
            messages = aiChatMapper.selectOfficialChatAIMessage(roomId);
            globalMessageMap.put(roomId, messages);
        }
        AiChatSession Session = aiChatSessionRepository.selectSessionBySessionId(roomId);

        // 4. 插入用户消息（官方ChatMessage）
        AiChatMessage userMsg = AiChatMessage.builder().chatSession(Session)
                .sendTime(LocalDateTime.now()).isRead(0).senderId(userId).build();
        userMsg.setRole(ChatMessageRole.USER); // 用户消息角色
        userMsg.setContent(message);
        aiChatMessageRepository.save(userMsg); // 插入消息（合并原insertUserMsg）
        messages.add(convertSingleAiMessage(userMsg));

        // 5. 调用AI生成回复
        String answer = aiManager.doChat(messages);

        // 6. 插入AI回复（官方ChatMessage，角色为ASSISTANT）
        AiChatMessage assistantMsg = AiChatMessage.builder().chatSession(Session)
                .sendTime(LocalDateTime.now()).isRead(0).build();
        assistantMsg.setRole(ChatMessageRole.ASSISTANT); // AI回复角色
        assistantMsg.setContent(answer);
        aiChatMessageRepository.save(assistantMsg);// 插入消息（合并原insertAIMsg）
        messages.add(convertSingleAiMessage(assistantMsg)); // 更新缓存

        // 7. 处理会话结束：若AI回复“游戏结束”
        if (answer.contains("游戏结束")) {
            // 7.1 清除内存缓存
            globalMessageMap.remove(roomId);
            // 7.2 逻辑删除会话记录（避免物理删除）
            aiChatSessionRepository.deleteBySessionId(roomId);
        }
        return answer;
    }

    public List<ChatMessage> convertToOfficialChatMessages(List<AiChatMessage> aiMessages) {
        return aiMessages.stream()
                .map(aiMsg -> ChatMessage.builder()
                        .role(aiMsg.getRole().toThirdParty())
                        .content(aiMsg.getContent())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 辅助方法：转换单条AiChatMessage（可选，用于复用逻辑）
     */
    private ChatMessage convertSingleAiMessage(AiChatMessage aiMsg) {
        return ChatMessage.builder()
                .role(aiMsg.getRole().toThirdParty()) // 映射成官方枚举
                .content(aiMsg.getContent())
                .build();
    }

    /**
     * 获取当前用户的AI聊天室列表（基础版）
     *
     * @param userId 当前用户ID
     * @return 聊天室列表
     */
    public List<AiChatSession> getAIChatRoomList(Long userId) {
        return aiChatMapper.selectValidSessionsByUserId(userId);
    }



    @Override
    public List<ChatListVO> getAIChatList(Long userId) {
        // 执行原生SQL查询
        List<Object[]> results = chatGameMemberRepository.findUserGameRooms(userId);

        // 转换结果为VO列表
        List<ChatListVO> vos = new ArrayList<>();
        for (Object[] row : results) {
            ChatListVO vo = new ChatListVO();

            // 设置房间ID
            vo.setRoomId((String) row[0]);

            // 设置房间标题
            vo.setTitle((String) row[1]);

            // 设置汤面内容
            vo.setSoupContent((String) row[2]);

            // 设置创建时间（处理不同类型的时间格式）
            if (row[3] != null) {
                if (row[3] instanceof LocalDateTime) {
                    vo.setCreateTime((LocalDateTime) row[3]);
                } else if (row[3] instanceof Timestamp) {
                    vo.setCreateTime(((Timestamp) row[3]).toLocalDateTime());
                } else {
                    // 尝试解析字符串格式的时间
                    try {
                        vo.setCreateTime(LocalDateTime.parse(row[3].toString()));
                    } catch (Exception e) {
                        // 如果解析失败，使用当前时间
                        vo.setCreateTime(LocalDateTime.now());
                    }
                }
            }

            vos.add(vo);
        }

        return vos;
    }

    @Override
    public getAIChatListDetailVO getAIChatListDetail(String roomId) {
        // 1. 获取房间基本信息
        ChatGame chatGame = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(404, "房间不存在"));
        HaiGuiSoup soup = haiGuiSoupRepository.findById(chatGame.getHaiGuiSoup().getSoupId())
                .orElseThrow(() -> new BusinessException(404, "该海龟汤不存在"));
        GameSession gameSession = gameSessionRepository.findById(chatGame.getSessionId())
                .orElseThrow(() -> new BusinessException(404, "游戏会话不存在"));

        // 2. 获取房间进度和所有任务
        List<HaiGuiRoomProgress> progressList = haiGuiRoomProgressRepository.findByRoomId(roomId);
        Map<Long, HaiGuiRoomProgress> progressMap = progressList.stream()
                .collect(Collectors.toMap(HaiGuiRoomProgress::getTaskId, Function.identity()));

        List<InferenceTask> allTasks = inferenceTaskRepository.findBySoupId(soup.getSoupId());

        // 3. 获取所有线索片段
        List<ClueFragment> allClues = clueFragmentRepository.findBySoupId(soup.getSoupId());

        // 4. 收集所有已触发的线索ID（全局）
        Set<Long> allTriggeredFragmentIds = new HashSet<>();
        for (HaiGuiRoomProgress progress : progressList) {
            if (progress.getTriggeredFragmentIds() != null) {
                allTriggeredFragmentIds.addAll(progress.getTriggeredFragmentIds());
            }
        }

        // 5. 构建全局已触发/未触发线索列表（单层循环）
        List<CompletedCluesResult> completedClues = new ArrayList<>();
        List<CompletedCluesResult> uncompletedClues = new ArrayList<>();

        for (ClueFragment clue : allClues) {
            CompletedCluesResult clueResult = convertToClueResult(clue);

            if (allTriggeredFragmentIds.contains(clue.getFragmentId())) {
                completedClues.add(clueResult);
            } else {
                uncompletedClues.add(clueResult);
            }
        }

        // 6. 处理任务完成情况（不含线索）
        List<CompletedTasksResult> completedTasks = new ArrayList<>();
        List<UncompletedTasksResult> uncompletedTasks = new ArrayList<>();

        for (InferenceTask task : allTasks) {
            HaiGuiRoomProgress progress = progressMap.get(task.getTaskId());
            boolean isCompleted = progress != null && progress.getCompleted();

            if (isCompleted) {
                CompletedTasksResult result = new CompletedTasksResult();
                result.setTaskId(task.getTaskId());
                result.setTaskName(task.getTaskName());
                result.setDescription(task.getTaskDescription());
                result.setCompletionTime(progress.getCompletionTime());
                completedTasks.add(result);
            } else {
                UncompletedTasksResult result = new UncompletedTasksResult();
                result.setTaskId(task.getTaskId());
                result.setTaskName(task.getTaskName());
                result.setDescription(task.getTaskDescription());
                uncompletedTasks.add(result);
            }
        }

        // 7. 获取聊天消息和问题
        List<HaiGuiChatMessageWithFragments> messages = haiGuiChatMessageRepository.findAllByRoomId(roomId);
        List<RoomGetClueVO.QuestionClass> questions = getQuestions(messages);

        // 8. 构建返回对象
        getAIChatListDetailVO endGameVO = new getAIChatListDetailVO();
        endGameVO.setRoomId(roomId);
        endGameVO.setSoupTitle(soup.getSoupTitle());
        endGameVO.setSoupSurface(soup.getSoupSurface());
        endGameVO.setSoupBottom(soup.getSoupBottom());
        endGameVO.setCurrentProgress(gameSession.getCurrentProgress().doubleValue());
        endGameVO.setFinalScore(gameSession.getScore().intValue());
        endGameVO.setCompletedTasks(completedTasks);
        endGameVO.setUncompletedTasks(uncompletedTasks);
        endGameVO.setTotalTasks(allTasks.size());
        endGameVO.setCompletedClues(completedClues); // 全局已触发线索
        endGameVO.setUncompletedClues(uncompletedClues); // 全局未触发线索
        endGameVO.setQuestion(questions);
        return endGameVO;
    }

    // 辅助方法：将ClueFragment转换为CompletedCluesResult
    private CompletedCluesResult convertToClueResult(ClueFragment clue) {
        CompletedCluesResult result = new CompletedCluesResult();
        result.setFragmentId(clue.getFragmentId());
        result.setFragmentContent(clue.getFragmentContent());
        result.setFragmentType(clue.getFragmentType());
        result.setDifficulty(clue.getDifficulty());
        result.setImportance(clue.getImportance());
        result.setTriggerKeywords(clue.getTriggerKeywords());
        result.setIsCoreClue(clue.getIsCoreClue());
        result.setFragmentOrder(clue.getFragmentOrder());
        return result;
    }

    // 辅助方法：从消息中提取问题
    public static List<RoomGetClueVO.QuestionClass> getQuestions(List<HaiGuiChatMessageWithFragments> messages) {
        return messages.stream()
                .map(message -> {
                    RoomGetClueVO.QuestionClass questionClass = new RoomGetClueVO.QuestionClass();
                    questionClass.setQuestion(message.getQuestionContent());
                    questionClass.setAnswer(message.getAiAnswer() != null ? message.getAiAnswer().getDescription() : "");
                    questionClass.setSendTime(message.getCreatedAt() != null ? message.getCreatedAt().toString() : "");
                    return questionClass;
                })
                .collect(Collectors.toList());
    }

}
