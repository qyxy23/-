package com.guanyu.haigui.controller;

import com.guanyu.haigui.Enum.VectorType;
import com.guanyu.haigui.service.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量测试控制器
 * 用于测试向量化功能
 */
@RestController
@RequestMapping("/api/test/vector")
@RequiredArgsConstructor
@Slf4j
public class VectorTestController {

    private final VectorService vectorService;

    /**
     * 测试向量化功能
     *
     * @param soupId 海龟汤ID
     * @return 测试结果
     */
    @PostMapping("/test/{soupId}")
    public ResponseEntity<Map<String, Object>> testVectorization(@PathVariable String soupId) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("开始测试向量化功能: soupId={}", soupId);

            // 1. 检查海龟汤是否已向量化
            boolean isVectorized = vectorService.isSoupVectorized(soupId);
            result.put("isVectorized", isVectorized);

            // 2. 获取向量元数据
            List<com.guanyu.haigui.pojo.model.VectorMetadata> vectors = vectorService.getSoupVectors(soupId);
            result.put("vectorCount", vectors.size());
            result.put("vectors", vectors.stream()
                    .map(v -> Map.of(
                            "vectorId", v.getVectorId(),
                            "vectorType", v.getVectorType(),
                            "redisKey", v.getRedisKey(),
                            "vectorDim", v.getVectorDim()
                    ))
                    .toList());

            // 3. 测试向量检索功能
            String testQuestion = "这个海龟汤的故事发生在什么时候？";
            Map<VectorType, List<VectorService.ContextMatchResult>> contextResults =
                    vectorService.findRelevantContext(testQuestion, soupId, 3);

            result.put("contextRetrieval", Map.of(
                    "question", testQuestion,
                    "contextTypes", contextResults.size(),
                    "results", contextResults
            ));

            result.put("success", true);
            result.put("message", "向量化测试完成");

            log.info("向量化测试完成: soupId={}, 向量数量={}, 检索到上下文类型数={}",
                    soupId, vectors.size(), contextResults.size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("向量化测试失败: soupId={}", soupId, e);
            result.put("success", false);
            result.put("message", "测试失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 检查向量状态
     *
     * @param soupId 海龟汤ID
     * @return 向量状态
     */
    @GetMapping("/status/{soupId}")
    public ResponseEntity<Map<String, Object>> getVectorStatus(@PathVariable String soupId) {
        Map<String, Object> result = new HashMap<>();

        try {
            boolean isVectorized = vectorService.isSoupVectorized(soupId);
            result.put("soupId", soupId);
            result.put("isVectorized", isVectorized);

            if (isVectorized) {
                List<com.guanyu.haigui.pojo.model.VectorMetadata> vectors = vectorService.getSoupVectors(soupId);
                result.put("vectorCount", vectors.size());
                result.put("vectorTypes", vectors.stream()
                        .map(v -> v.getVectorType().name())
                        .distinct()
                        .toList());
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("获取向量状态失败: soupId={}", soupId, e);
            result.put("error", "获取状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Vector service is running");
    }
}