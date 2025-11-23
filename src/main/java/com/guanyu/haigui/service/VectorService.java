package com.guanyu.haigui.service;

import com.guanyu.haigui.Enum.VectorType;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.SoupClue;
import com.guanyu.haigui.pojo.model.VectorMetadata;

import java.util.List;
import java.util.Map;

/**
 * 向量服务接口
 * 提供文本向量化、存储和检索功能
 */
public interface VectorService {

    /**
     * 向量化并存储海龟汤的所有上下文内容
     * 包括汤面、汤底、主持人手册和所有线索
     *
     * @param soup 海龟汤对象
     * @param clues 线索列表
     * @return 是否成功
     */
    boolean vectorizeAndStoreSoupContext(HaiGuiSoup soup, List<SoupClue> clues);

    /**
     * 向量化并存储汤面
     *
     * @param soupId 海龟汤ID
     * @param soupSurface 汤面文本
     * @return 向量元数据
     */
    VectorMetadata vectorizeSoupSurface(String soupId, String soupSurface);

    /**
     * 向量化并存储汤底
     *
     * @param soupId 海龟汤ID
     * @param soupBottom 汤底文本
     * @return 向量元数据
     */
    VectorMetadata vectorizeSoupBottom(String soupId, String soupBottom);

    /**
     * 向量化并存储主持人手册
     *
     * @param soupId 海龟汤ID
     * @param hostManual 主持人手册文本
     * @return 向量元数据
     */
    VectorMetadata vectorizeHostManual(String soupId, String hostManual);

    /**
     * 向量化并存储线索
     *
     * @param soupId 海龟汤ID
     * @param clue 线索对象
     * @return 向量元数据
     */
    VectorMetadata vectorizeClue(String soupId, SoupClue clue);

    /**
     * 批量向量化线索
     *
     * @param soupId 海龟汤ID
     * @param clues 线索列表
     * @return 向量元数据列表
     */
    List<VectorMetadata> vectorizeClues(String soupId, List<SoupClue> clues);

    /**
     * 将玩家问题转换为向量
     *
     * @param question 玩家问题
     * @return 向量数组
     */
    List<Float> vectorizeQuestion(String question);

    /**
     * 在Redis Stack中搜索相似的向量
     *
     * @param queryVector 查询向量
     * @param vectorType 向量类型
     * @param topK 返回前K个结果
     * @return 匹配结果（包含ID和相似度分数）
     */
    Map<String, Double> searchSimilarVectors(List<Float> queryVector, VectorType vectorType, int topK);

    /**
     * 在Redis Stack中搜索相似的向量（限定海龟汤范围）
     *
     * @param queryVector 查询向量
     * @param soupId 海龟汤ID
     * @param topK 返回前K个结果
     * @return 匹配结果（包含向量类型、ID和相似度分数）
     */
    Map<String, Map<String, Double>> searchSimilarVectorsWithinSoup(List<Float> queryVector, String soupId, int topK);

    /**
     * 获取指定海龟汤的所有相关上下文向量
     *
     * @param soupId 海龟汤ID
     * @return 向量元数据列表
     */
    List<VectorMetadata> getSoupVectors(String soupId);

    /**
     * 获取指定类型的向量
     *
     * @param soupId 海龟汤ID
     * @param vectorType 向量类型
     * @return 向量元数据
     */
    VectorMetadata getSoupVector(String soupId, VectorType vectorType);

    /**
     * 获取Redis中存储的向量数据
     *
     * @param redisKey Redis键名
     * @return 向量数组
     */
    List<Float> getVectorFromRedis(String redisKey);

    /**
     * 删除海龟汤的所有向量数据
     *
     * @param soupId 海龟汤ID
     * @return 是否成功
     */
    boolean deleteSoupVectors(String soupId);

    /**
     * 更新向量数据
     *
     * @param soup 海龟汤对象
     * @param clues 线索列表
     * @return 是否成功
     */
    boolean updateSoupVectors(HaiGuiSoup soup, List<SoupClue> clues);

    /**
     * 检查海龟汤是否已向量化
     *
     * @param soupId 海龟汤ID
     * @return 是否已向量化
     */
    boolean isSoupVectorized(String soupId);

    /**
     * 根据上下文内容获取相关的向量匹配结果
     * 用于玩家问题回答的上下文检索
     *
     * @param question 玩家问题
     * @param soupId 海龟汤ID
     * @param topK 返回前K个结果
     * @return 匹配的上下文信息
     */
    Map<VectorType, List<ContextMatchResult>> findRelevantContext(String question, String soupId, int topK);

    /**
     * 上下文匹配结果类
     */
    class ContextMatchResult {
        private String id;          // 可以是soupId、clueId等
        private String content;     // 原始文本内容
        private Double similarity;  // 相似度分数
        private VectorType type;    // 向量类型

        public ContextMatchResult(String id, String content, Double similarity, VectorType type) {
            this.id = id;
            this.content = content;
            this.similarity = similarity;
            this.type = type;
        }

        // Getters
        public String getId() { return id; }
        public String getContent() { return content; }
        public Double getSimilarity() { return similarity; }
        public VectorType getType() { return type; }
    }
}