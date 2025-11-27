package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 线索拆解服务
 * 将汤底拆解为可向量化的线索片段
 */
@Service
@Slf4j
public class ClueDecompositionService {

    private final AIManager aiManager;
    private final ObjectMapper objectMapper;
    private final BgeVectorClientUtil vectorClient;

    public ClueDecompositionService(AIManager aiManager, ObjectMapper objectMapper, BgeVectorClientUtil vectorClient) {
        this.aiManager = aiManager;
        this.objectMapper = objectMapper;
        this.vectorClient = vectorClient;
    }

    /**
     * 拆解汤底为线索片段
     */
    @SuppressWarnings("unchecked")
    public List<ClueFragment> decomposeSoupBottom(String soupTitle, String soupSurface, String soupBottom) {
        try {
            log.info("开始拆解汤底线索，标题: {}", soupTitle);

            // 生成拆解提示词
            String prompt = generateDecompositionPrompt(soupTitle, soupSurface, soupBottom);

            // 调用AI进行拆解
            String systemPrompt = "你是专业的海龟汤分析师，擅长将复杂的故事真相拆解为层次分明的线索片段。请严格按照JSON格式返回结果。";
            String aiResponse = aiManager.doChat(systemPrompt, prompt);

            log.info("AI返回线索拆解响应，长度: {}", aiResponse.length());

            // 解析AI响应
            Map<String, Object> response = objectMapper.readValue(aiResponse, Map.class);
            List<Map<String, Object>> fragmentsData = (List<Map<String, Object>>) response.get("fragments");

            // 转换为ClueFragment对象
            List<ClueFragment> fragments = fragmentsData.stream()
                    .map(this::mapToClueFragment)
                    .collect(Collectors.toList());

            // 为每个片段生成向量
            generateVectorsForFragments(fragments);

            log.info("成功拆解出{}个线索片段", fragments.size());
            return fragments;

        } catch (Exception e) {
            log.error("拆解汤底线索失败", e);
            return generateFallbackFragments(soupTitle, soupSurface, soupBottom);
        }
    }

    /**
     * 生成拆解提示词
     */
    private String generateDecompositionPrompt(String soupTitle, String soupSurface, String soupBottom) {
        return String.format("""
            请将这个海龟汤的汤底（真相）拆解为多个线索片段，每个片段代表一个推理层次的信息。

            === 基本信息 ===
            标题：%s
            汤面（玩家看到的悬念）：%s
            汤底（完整真相）：%s

            === 拆解要求 ===
            1. 按推理深度分层：从表层信息到深层真相
            2. 每个片段独立完整，便于向量化匹配
            3. 标注片段类型和关键信息
            4. 设计合理的匹配阈值
            5. 关联相应的推理任务

            === 推理层次说明 ===
            **层次1-表层信息**：直接观察到的事实
            **层次2-中层推理**：需要简单推理才能发现的信息
            **层次3-深层真相**：隐藏的关键真相
            **层次4-核心逻辑**：整个故事的底层逻辑

            === 片段类型说明 ===
            **TIME**：时间相关（年代、时期、时间跨度）
            **PLACE**：地点相关（具体位置、环境特征）
            **CHARACTER**：人物相关（身份、关系、数量）
            **PLOT**：情节相关（事件、过程、结果）
            **OBJECT**：物品相关（关键物品、道具）
            **TRUTH**：真相相关（核心秘密、深层逻辑）

            === 输出要求 ===
            请严格按照以下JSON格式返回：

            {
              "fragments": [
                {
                  "content": "线索片段的具体内容",
                  "fragmentType": "片段类型（TIME/PLACE/CHARACTER/PLOT/OBJECT/TRUTH）",
                  "inferenceLevel": 1,
                  "keywords": ["关键词1", "关键词2"],
                  "isCoreClue": false,
                  "similarityThreshold": 0.7,
                  "associatedTasks": [1, 2],
                  "order": 1
                }
              ]
            }

            重要要求：
            - 生成8-15个线索片段
            - 确保推理层次分布合理
            - 内容要简洁明确，便于向量匹配
            - 关键词要符合玩家提问习惯
            - 为每个片段设置合适的相似度阈值
            """, soupTitle, soupSurface, soupBottom);
    }

    /**
     * 转换AI响应为ClueFragment对象
     */
    @SuppressWarnings("unchecked")
    private ClueFragment mapToClueFragment(Map<String, Object> data) {
        ClueFragment fragment = new ClueFragment();
        fragment.setFragmentContent((String) data.get("content"));
        fragment.setFragmentType((String) data.get("fragmentType"));
        fragment.setInferenceLevel((Integer) data.get("inferenceLevel"));
        fragment.setTriggerKeywords((List<String>) data.get("keywords"));
        fragment.setIsCoreClue((Boolean) data.getOrDefault("isCoreClue", false));
        fragment.setSimilarityThreshold(((Number) data.getOrDefault("similarityThreshold", 0.7)).doubleValue());
        fragment.setAssociatedTaskIds((List<Integer>) data.getOrDefault("associatedTasks", new ArrayList<>()));
        fragment.setFragmentOrder((Integer) data.getOrDefault("order", 0));
        fragment.setGenerationSource("AI");
        fragment.setAiAnalysisConfidence(0.9);
        fragment.setIsDeleted(false);
        return fragment;
    }

    /**
     * 为线索片段生成向量
     */
    private void generateVectorsForFragments(List<ClueFragment> fragments) {
        for (ClueFragment fragment : fragments) {
            try {
                // 生成向量化文本（内容+关键词）
                String keywords = fragment.getTriggerKeywords() != null ? String.join(" ", fragment.getTriggerKeywords()) : "";
                String vectorText = fragment.getFragmentContent() + " " + keywords;

                // 调用BGE向量服务
                SingleEncodeResponse response = vectorClient.encodeSingle(vectorText);
                List<Double> vector = response.getEmbeddings().get(0)
                        .stream()
                        .map(Float::doubleValue)
                        .collect(java.util.stream.Collectors.toList());

                // 生成向量哈希
                String vectorHash = generateVectorHash(vector);

                fragment.setVectorData(vector);
                fragment.setVectorHash(vectorHash);

                log.debug("为片段生成向量完成: {}", fragment.getFragmentContent());

            } catch (Exception e) {
                log.error("为片段生成向量失败: {}", fragment.getFragmentContent(), e);
                // 生成空向量避免中断
                fragment.setVectorData(new ArrayList<>());
                fragment.setVectorHash("");
            }
        }
    }

    /**
     * 生成向量数据的哈希值
     */
    private String generateVectorHash(List<Double> vector) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String vectorString = vector.stream()
                    .map(d -> String.format("%.6f", d))
                    .collect(Collectors.joining(","));
            byte[] hash = md.digest(vectorString.getBytes());
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
            log.error("生成向量哈希失败", e);
            return "";
        }
    }

    /**
     * 生成备用线索片段（当AI失败时）
     */
    private List<ClueFragment> generateFallbackFragments(String soupTitle, String soupSurface, String soupBottom) {
        log.warn("使用备用方案生成线索片段");

        // 基于汤底内容生成通用片段
        List<ClueFragment> fragments = new ArrayList<>();

        // 添加基本信息片段
        fragments.add(createBasicFragment("故事背景", "PLACE", 1, 0.8));
        fragments.add(createBasicFragment("时间设定", "TIME", 1, 0.8));
        fragments.add(createBasicFragment("主要人物", "CHARACTER", 2, 0.7));
        fragments.add(createBasicFragment("关键事件", "PLOT", 2, 0.7));
        fragments.add(createBasicFragment("核心真相", "TRUTH", 4, 0.9));

        return fragments;
    }

    /**
     * 创建基础线索片段
     */
    private ClueFragment createBasicFragment(String content, String type, int level, double threshold) {
        ClueFragment fragment = new ClueFragment();
        fragment.setFragmentContent(content);
        fragment.setFragmentType(type);
        fragment.setInferenceLevel(level);
        fragment.setTriggerKeywords(Arrays.asList(content.split(" ")));
        fragment.setIsCoreClue(level >= 3);
        fragment.setSimilarityThreshold(threshold);
        fragment.setAssociatedTaskIds(new ArrayList<>());
        fragment.setFragmentOrder(0);
        fragment.setGenerationSource("FALLBACK");
        fragment.setAiAnalysisConfidence(0.5);
        fragment.setVectorData(new ArrayList<>());
        fragment.setVectorHash("");
        fragment.setIsDeleted(false);
        return fragment;
    }
}