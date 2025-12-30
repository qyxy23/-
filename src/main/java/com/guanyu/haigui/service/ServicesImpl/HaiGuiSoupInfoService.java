package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.Resource;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.guanyu.haigui.Exception.AiResponseException;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.Info.ClueFragmentInfo;
import com.guanyu.haigui.pojo.Info.InferenceTaskInfo;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
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
import java.math.BigDecimal;
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


    public String generatePrompt(HaiGuiSoupAudit dto) {
        int fragmentCount = generateFragmentCount(dto);
        return String.format("""
        # 输入参数
        - 标题：《%s》
        - 汤面：%s
        - 汤底：%s
        - 预计时长：%d分钟
        - 难度：%s
        - 标签：%s
        
        # 输出要求
        请严格按以下JSON格式输出，包含两个顶级字段：
        1. hostManual（主持人手册文本）
        2. fragments（线索数组）
        3. tasks（任务数组）
        
        ## 主持人手册规范
        - 包含游戏简介、主持流程、控场技巧、时间管理建议
        - 内容以精华凝练为主，使用Markdown格式分段
        
        ## 线索(fragments)规范
        - 建议数量为%d条(线索内容要求一定是要能够从汤底中找到的，如果实在找不到足够数量的线索，也不要自己编造汤底没有的线索内容)
        - 字段说明：
          • content: 线索内容（50-150字）
          • triggerKeywords: 触发关键词数组(2-4个)
        
        ## 任务(tasks)规范
        - 数量严格3-5个
        - 所有任务progressWeight总和=100
        - 字段说明：
          • taskName: 任务名称（10字内）
          • taskDescription: 任务描述（50字内）
          • targetKeywords: 目标关键词数组(2-3个)
          • reasoningGoal: 推理目标（20字内）
          • progressWeight: 进度权重（总和100）
          • taskOrder: 顺序号(从1开始)
          • prerequisiteFragmentIds: 前置线索序号数组（整数数组，表示需要哪些线索）
        
        # 任务解锁规则
        - 任务间相互独立，无需按顺序完成
        - 解锁条件：获得所有前置线索(prerequisiteFragmentIds)
        - 线索序号：fragments数组的第一条线索序号为1，第二条为2，以此类推
        
        # 特别约束
        1. 返回标准JSON，控制字符用\\n转义
        2. 避免重复关键词
        3. 时间类线索占比≤20%%
        
        {
          "hostManual": "### 主持指南...",
          "fragments": [
            {
              "content": "线索片段内容",
              "triggerKeywords": ["关键词1", "关键词2"]
            }
          ],
          "tasks": [
            {
              "taskName": "任务名称",
              "taskDescription": "任务描述",
              "targetKeywords": ["关键词1", "关键词2"],
              "reasoningGoal": "推理目标描述",
              "progressWeight": 30.0,
              "taskOrder": 1,
              "prerequisiteFragmentIds": [1, 3]
            }
          ]
        }
        """,
                dto.getTitle(),
                dto.getSurface(),
                dto.getBottom(),
                dto.getEstimatedDuration(),
                dto.getDifficultyLevel().name(),
                dto.getTags(),
                fragmentCount
        );
    }


    public int generateFragmentCount(HaiGuiSoupAudit audit) {
        int soupLength = audit.getBottom().length();
        int difficultyLevel = audit.getDifficultyLevel().ordinal();
        // 基础片段数
        double baseFragmentCount = 8.0;
        double lengthFactor = 0.05;
        double difficultyFactor = 0.5;
        int targetFragmentCount = (int) (baseFragmentCount +
                soupLength * lengthFactor +
                difficultyLevel * difficultyFactor);
        //TODO:当前片段还是太多，故改成原片段数量-5
        return Math.max(8, Math.min(targetFragmentCount-5, 30));
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
        List<String> fragmentIdList = new ArrayList<>();
        String soupFragmentsKey = String.format("hai_gui:soup:%s:fragment", soup.getSoupId());
        List<ClueFragment> clueFragments = new ArrayList<>();

        int order = 1; // 从1开始的顺序号
        for (ClueFragmentInfo fragment : fragments) {
            ClueFragment clueFragment = new ClueFragment();
            clueFragment.setSoupId(soup.getSoupId());
            clueFragment.setFragmentContent(fragment.getFragmentContent());
            clueFragment.setTriggerKeywords(fragment.getTriggerKeywords());
            clueFragment.setVectorData(vectorizeFragment(fragment.getFragmentContent()));
            clueFragment.setIsDeleted(false);
            clueFragment.setCreatedAt(LocalDateTime.now());
            clueFragment.setUpdatedAt(LocalDateTime.now());

            ClueFragment savedFragment = clueFragmentRepository.saveAndFlush(clueFragment);
            clueFragments.add(savedFragment);

            // 存储到Redis
            String soupFragmentKey = String.format("hai_gui:soup:%s:fragment:%s",
                    soup.getSoupId(), savedFragment.getFragmentId());
            redisClient.storeVector(soupFragmentKey, savedFragment.getVectorData());

            fragmentIdList.add(savedFragment.getFragmentId().toString());
            fragmentOrderToIdMap.put(order, savedFragment.getFragmentId()); // 使用顺序号作为键
            order++;
        }

        if (!fragmentIdList.isEmpty()) {
            redisClient.add(soupFragmentsKey, fragmentIdList);
        }

        updateSoupWithClueInfo(soup, clueFragments);
        return fragmentOrderToIdMap;
    }

    // 简化后的向量化方法
    private List<Double> vectorizeFragment(String content) {
        try {
            SingleEncodeResponse response = BgeVectorClientUtil.encodeSingle(content);
            if (response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
                return Collections.emptyList();
            }

            // 将Float转换为Double
            return response.getEmbeddings().get(0).stream()
                    .map(Float::doubleValue)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("向量化线索失败: {}", content, e);
            return Collections.emptyList();
        }
    }




    public List<InferenceTask> convertToInferenceTasks(List<InferenceTaskInfo> tasks,
                                                       HaiGuiSoup soup,
                                                       Map<Integer, Long> fragmentOrderToIdMap) {
        List<InferenceTask> inferenceTaskList = new ArrayList<>();

        for (InferenceTaskInfo task : tasks) {
            InferenceTask inferenceTask = new InferenceTask();
            inferenceTask.setSoupId(soup.getSoupId());
            inferenceTask.setTaskName(task.getTaskName());
            inferenceTask.setTaskDescription(task.getTaskDescription());
            inferenceTask.setTargetKeywords(task.getTargetKeywords());
            inferenceTask.setReasoningGoal(task.getReasoningGoal());
            inferenceTask.setProgressWeight(BigDecimal.valueOf(task.getProgressWeight()));
            inferenceTask.setTaskOrder(task.getTaskOrder());
            inferenceTask.setIsDeleted(false);
            inferenceTask.setCreatedAt(LocalDateTime.now());
            inferenceTask.setUpdatedAt(LocalDateTime.now());

            // 转换前置线索序号为实际ID
            List<Long> fragmentIds = task.getPrerequisiteFragmentIds().stream()
                    .map(Long::intValue)               // 将Long转换为Integer
                    .map(fragmentOrderToIdMap::get)     // 用Integer键查询Map
                    .filter(Objects::nonNull)           // 过滤无效映射
                    .collect(Collectors.toList());

            inferenceTask.setPrerequisiteFragmentIds(fragmentIds);
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


    public HaiGuiInfoResult getFragmentsAndTasks(JsonNode draftFragments, JsonNode draftTasks) {
        return HaiGuiInfoUtil.getHaiGuiInfo(draftFragments, draftTasks);
    }
}
