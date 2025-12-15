package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.Resource;
import com.google.gson.Gson;
import com.guanyu.haigui.Exception.AiResponseException;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.Info.ClueFragmentInfo;
import com.guanyu.haigui.pojo.Info.InferenceTaskInfo;
import com.guanyu.haigui.pojo.dto.CreateTurtleSoupDTO;
import com.guanyu.haigui.pojo.dto.HaiGuiInfoGenerateDTO;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.InferenceTask;
import com.guanyu.haigui.pojo.result.HaiGuiInfoResult;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.repository.ClueFragmentRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.InferenceTaskRepository;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.HaiGuiInfoUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HaiGuiSoupInfoService {
    private final AIManager aiManager;
    private final RedisStackClient redisClient;
    private final HaiGuiInfoUtil haiGuiInfoUtil;
    private final ClueFragmentRepository clueFragmentRepository;
    private final InferenceTaskRepository inferenceTaskRepository;
    private final HaiGuiSoupRepository haiGuiSoupRepository;


    @Value("${haiqutang.ai.debug-mode:false}")
    private boolean debugMode;


    public String generatePrompt(HaiGuiInfoGenerateDTO dto) {
        int fragmentCount = generateFragmentCount(dto);
        return String.format("""
                                    # 输入参数
                                    - 标题：《%s》
                                    - 汤面：%s
                                    - 汤底：%s
                                    - 预计时长：%d分钟
                                    - 难度：%s
                                    - 标签：%s
                                    - 需生成线索数量：%d条
                        
                                    # 输出要求
                                    请严格按以下JSON格式输出，包含三个顶级字段：
                                    1. hostManual（主持人手册文本）
                                    2. fragments（线索数组）
                                    3. tasks（任务数组）
                        
                                    ## 主持人手册规范
                                    - 包含游戏简介、主持流程、控场技巧、时间管理建议
                                    - 字数300字左右，使用Markdown格式分段
                        
                                    ## 线索(fragments)规范
                                    - 数量严格等于%d条
                                    - 字段说明：
                                      • content: 线索内容（50-150字）
                                      • fragmentType: 枚举值[TIME,PLACE,CHARACTER,PLOT,OBJECT]
                                      • inferenceLevel: 推理等级(1-5)
                                      • difficulty: 难度(1-5)
                                      • importance: 重要性(1-10)
                                      • similarityThreshold: 相似度阈值(0.1-1.0)
                                      • isCoreClue: 是否核心线索(true/false)
                                      • fragmentOrder: 顺序号(从1开始)
                                      • triggerKeywords: 触发关键词数组(2-4个)
                        
                                    ## 任务(tasks)规范
                                    - 数量严格3-5个
                                    - 所有任务progressWeight总和=100
                                    - 字段说明：
                                      • taskName: 任务名称（10字内）
                                      • taskDescription: 任务描述（50字内）
                                      • understandingLevel: 理解等级(1-5)
                                      • targetKeywords: 目标关键词数组(2-3个)
                                      • reasoningGoal: 推理目标（20字内）
                                      • progressWeight: 进度权重（总和100）
                                      • isMandatory: 是否强制(true/false)
                                      • taskOrder: 顺序号(从1开始)
                                      • prerequisiteFragmentIds: 前置线索ID数组（必须包含≥1个线索ID）
                        
                                    # 任务解锁规则
                                    - 任务间相互独立，无需按顺序完成
                                    - 解锁条件：获得所有前置线索(prerequisiteFragmentIds)
                                    - 示例：任务B只需线索3和5，即使任务A未完成也可解锁
                        
                                    # 特别约束
                                    1. 线索ID使用fragmentOrder值（如第一条线索ID=1）
                                    2. 任务间权重分配示例：{30,30,40}或{25,25,25,25}
                                    3. 避免重复关键词
                                    4. 核心线索(isCoreClue)占比30%%-50%%
                                    5. 时间类线索(TIME)占比≤20%%
                                    6.返回标准JSON，控制字符用\\n转义
                        
                        
                                    {
                                      "hostManual": "### 主持指南...",
                                      "fragments": [
                                        {
                                          "content": "线索片段内容",
                                          "fragmentType": "TIME",
                                          "inferenceLevel": 1,
                                          "difficulty": 2,
                                          "importance": 5,
                                          "similarityThreshold": 0.7,
                                          "isCoreClue": false,
                                          "fragmentOrder": 1,
                                          "generationSource": "AI",
                                          "triggerKeywords": ["关键词1", "关键词2"]
                                        }
                                      ],
                                      "tasks": [
                                        {
                                          "taskName": "任务名称",
                                          "taskDescription": "任务描述",
                                          "understandingLevel": 2,
                                          "targetKeywords": ["关键词1", "关键词2"],
                                          "reasoningGoal": "推理目标描述",
                                          "progressWeight": 30.0,
                                          "isMandatory": true,
                                          "taskOrder": 1,
                                          "prerequisiteFragmentIds": [1, 3]
                                        }
                                      ]
                                    }
                        """,
                dto.getSoupTitle(),          // %s
                dto.getSoupSurface(),         // %s
                dto.getSoupBottom(),          // %s
                dto.getEstimatedDuration(),   // %d
                dto.getDifficultyLevel().name(), // %s
                dto.getTag().name(),          // %s
                fragmentCount,                // %d (需生成线索数量)
                fragmentCount                 // %d (数量严格等于%d条)
        );
    }


    public int generateFragmentCount(HaiGuiInfoGenerateDTO soup) {
        int soupLength = soup.getSoupBottom().length();
        int difficultyLevel = soup.getDifficultyLevel().ordinal();
        // 基础片段数
        double baseFragmentCount = 8.0;
        double lengthFactor = 0.05;
        double difficultyFactor = 0.5;
        int targetFragmentCount = (int) (baseFragmentCount +
                soupLength * lengthFactor +
                difficultyLevel * difficultyFactor);
        return Math.max(8, Math.min(targetFragmentCount, 30));
    }

    public HaiGuiInfoResult generateInfo(String prompt) {
        try {
            String aiResponse;
            if (!debugMode) {
                String systemPrompt = "你是一位专业的海龟汤游戏设计师，需要根据用户提供的汤面/汤底信息生成完整的游戏配置模板。";
                aiResponse = aiManager.doChat(systemPrompt, prompt);
                log.info("使用AI响应，长度: {}", aiResponse.length());
            } else {
                // 调试模式：从文件读取预存响应
                aiResponse = readAiResponseFromFile("temp/aiResponse3.txt");
                log.info("使用预存AI响应，长度: {}", aiResponse.length());
            }
            return haiGuiInfoUtil.parserHaiGuiInfo(aiResponse);
        } catch (Exception e) {
            throw new AiResponseException("AI响应异常");
        }
    }

    private String readAiResponseFromFile(String fileName) throws IOException {
        Resource resource = new ClassPathResource(fileName);
        try (
            InputStream inputStream = resource.getStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        }
    }


    public Map<Integer, Long> convertToClueFragmentsAndSave(List<ClueFragmentInfo> fragments, HaiGuiSoup soup) {
        Map<Integer, Long> fragmentOrderToIdMap = new HashMap<>();
        List<String> fragmentIdList = new ArrayList<>(); // 临时收集fragmentId（字符串形式）
        String soupFragmentsKey = String.format("hai_gui:soup:%s:fragment", soup.getSoupId()); // Redis集合键
        List<ClueFragment> clueFragments = new ArrayList<>();
        for (ClueFragmentInfo fragment : fragments) {
            ClueFragment clueFragment = new ClueFragment();
            clueFragment.setFragmentId(null);
            clueFragment.setSoupId(soup.getSoupId());
            clueFragment.setFragmentContent(fragment.getFragmentContent());
            clueFragment.setFragmentType(fragment.getFragmentType());
            clueFragment.setInferenceLevel(fragment.getInferenceLevel());
            List<Float> FragmentVector = vectorizeFragment(fragment.getFragmentContent());
            clueFragment.setVectorData(FragmentVector);

            // 确保向量数据不为null
            if (clueFragment.getVectorData() == null) {
                clueFragment.setVectorData(new ArrayList<>());
            }

            clueFragment.setDifficulty(fragment.getDifficulty());
            clueFragment.setImportance(fragment.getImportance());
            clueFragment.setTriggerKeywords(fragment.getTriggerKeywords());
            clueFragment.setSimilarityThreshold(fragment.getSimilarityThreshold());
            clueFragment.setIsCoreClue(fragment.getIsCoreClue());
            clueFragment.setFragmentOrder(fragment.getFragmentOrder());
            clueFragment.setGenerationSource(fragment.getGenerationSource());
            clueFragment.setIsDeleted(false);
            clueFragment.setCreatedAt(LocalDateTime.now());
            clueFragment.setUpdatedAt(LocalDateTime.now());
            ClueFragment savedFragment = clueFragmentRepository.saveAndFlush(clueFragment);
            clueFragments.add(savedFragment);
            String soupFragmentKey = String.format("hai_gui:soup:%s:fragment:%s", soup.getSoupId(), clueFragment.getFragmentId());
            redisClient.storeVector(soupFragmentKey, clueFragment.getVectorData());
            fragmentIdList.add(savedFragment.getFragmentId().toString());
            fragmentOrderToIdMap.put(savedFragment.getFragmentOrder(), savedFragment.getFragmentId());
        }
        if (!fragmentIdList.isEmpty()) {
            redisClient.add(soupFragmentsKey,fragmentIdList);
            // 使用Redis的SADD命令批量添加成员（支持多个参数）
            log.info("批量插入Redis集合: key={}, 成员数={}", soupFragmentsKey, fragmentIdList.size());
        }
        updateSoupWithClueInfo(soup,clueFragments);
        return fragmentOrderToIdMap;
    }


    public List<Float> vectorizeFragment(String FragmentVector) {
        try {
            log.debug("开始向量化线索: {}", FragmentVector.substring(0, Math.min(30, FragmentVector.length())));

            // 使用BGE模型向量化
            SingleEncodeResponse response = BgeVectorClientUtil.encodeSingle(FragmentVector);
            if (response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
                log.error("BGE向量化失败: {}", FragmentVector);
                return Collections.emptyList();
            }

            List<Float> vector = response.getEmbeddings().get(0);
            log.debug("线索向量化成功，维度: {}", vector.size());

            return vector;

        } catch (Exception e) {
            log.error("向量化线索失败: {}", FragmentVector, e);
            return Collections.emptyList();
        }
    }

    public List<InferenceTask> convertToInferenceTasks(CreateTurtleSoupDTO createTurtleSoupDTO, HaiGuiSoup soup, Map<Integer, Long> fragmentOrderToIdMap) {
        List<InferenceTaskInfo> inferenceTasks = createTurtleSoupDTO.getInferenceTasks();
        List<InferenceTask> inferenceTaskList = new ArrayList<>();
        for (InferenceTaskInfo task : inferenceTasks){
            InferenceTask inferenceTask = new InferenceTask();
            inferenceTask.setTaskId(null);
            inferenceTask.setSoupId(soup.getSoupId());
            inferenceTask.setTaskName(task.getTaskName());
            inferenceTask.setTaskDescription(task.getTaskDescription());
            inferenceTask.setUnderstandingLevel(task.getUnderstandingLevel());
            inferenceTask.setTargetKeywords(task.getTargetKeywords());
            inferenceTask.setReasoningGoal(task.getReasoningGoal());
            inferenceTask.setProgressWeight(task.getProgressWeight());
            inferenceTask.setIsMandatory(task.getIsMandatory());
            inferenceTask.setTaskOrder(task.getTaskOrder());
            // Set<Long> fragmentIds = new HashSet<>();
            // for(Long order : task.getPrerequisiteFragmentIds()){
            //     Long fragmentId = fragmentOrderToIdMap.get(order);
            //     if (fragmentId != null) {
            //         fragmentIds.add(fragmentId);
            //     } else {
            //         log.warn("未找到片段序号为{}的片段，跳过", order);
            //     }
            // }
            // inferenceTask.setPrerequisiteFragmentIds(fragmentIds);
            //该段代码替换为下方的stream流代码
            inferenceTask.setPrerequisiteFragmentIds(task.getPrerequisiteFragmentIds().stream()
                    .map(fragmentOrderToIdMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));

            inferenceTask.setIsDeleted(false);
            inferenceTask.setCreatedAt(LocalDateTime.now());
            inferenceTask.setUpdatedAt(LocalDateTime.now());
            inferenceTaskList.add(inferenceTask);
        }
        return inferenceTaskRepository.saveAll(inferenceTaskList);
    }


    // 更新海龟汤线索信息的辅助方法
    private void updateSoupWithClueInfo(HaiGuiSoup soup, List<ClueFragment> fragments) {
        List<Long> fragmentIds = fragments.stream()
                .map(ClueFragment::getFragmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        soup.setKeyClues(new Gson().toJson(fragmentIds));
        soup.setUpdatedAt(LocalDateTime.now());
        haiGuiSoupRepository.saveAndFlush(soup);
    }

}
