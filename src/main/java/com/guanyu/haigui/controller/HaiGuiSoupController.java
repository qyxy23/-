package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.CreateHaiGuiSoupDTO;
import com.guanyu.haigui.pojo.dto.SimpleSoupRequest;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.ServicesImpl.TurtleSoupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 海龟汤管理控制器
 * 提供海龟汤的CRUD操作以及向量搜索功能
 */
@RestController
@RequestMapping("/api/haigui/soup")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "海龟汤管理", description = "海龟汤的创建、搜索、推荐等操作")
public class HaiGuiSoupController {

    private final TurtleSoupService turtleSoupService;

    /**
     * 创建海龟汤（包含向量化）
     */
    @PostMapping("/create")
    @Operation(summary = "创建海龟汤", description = "创建新的海龟汤并自动向量化存储")
    public Result<String> createSoup(@RequestBody CreateHaiGuiSoupDTO soup) {
        try {
            boolean success = turtleSoupService.addTurtleSoup(soup);
            if (success) {
                return Result.success("海龟汤创建成功");
            } else {
                return Result.error("海龟汤创建失败");
            }
        } catch (Exception e) {
            log.error("创建海龟汤失败", e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新海龟汤（包含向量更新）
     */
    @PutMapping("/update")
    @Operation(summary = "更新海龟汤", description = "更新海龟汤信息并重新向量化")
    public Result<String> updateSoup(@RequestBody HaiGuiSoup soup) {
        try {
            if (soup.getSoupId() == null || soup.getSoupId().isEmpty()) {
                return Result.error("海龟汤ID不能为空");
            }

            boolean success = turtleSoupService.updateTurtleSoup(soup);
            if (success) {
                return Result.success("海龟汤更新成功", soup.getSoupId());
            } else {
                return Result.error("海龟汤更新失败");
            }
        } catch (Exception e) {
            log.error("更新海龟汤失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除海龟汤（包含向量清理）
     */
    @DeleteMapping("/delete/{soupId}")
    @Operation(summary = "删除海龟汤", description = "删除海龟汤及其向量数据")
    public Result<String> deleteSoup(@PathVariable String soupId) {
        try {
            boolean success = turtleSoupService.deleteTurtleSoup(soupId);
            if (success) {
                return Result.success("海龟汤删除成功", soupId);
            } else {
                return Result.error("海龟汤删除失败");
            }

        } catch (Exception e) {
            log.error("删除海龟汤失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 基于玩家问题的向量搜索
     */
    @PostMapping("/search")
    @Operation(summary = "搜索海龟汤", description = "基于玩家问题进行向量相似度搜索")
    public Result<Map<String, Double>> searchSoups(
            @Parameter(description = "玩家问题") @RequestParam String question,
            @Parameter(description = "返回结果数量") @RequestParam(defaultValue = "10") int topK) {
        try {
            Map<String, Double> results = turtleSoupService.findMatchingSoup(question, topK);
            return Result.success("搜索完成", results);

        } catch (Exception e) {
            log.error("搜索海龟汤失败", e);
            return Result.error("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 获取海龟汤推荐
     */
    @GetMapping("/recommend/{soupId}")
    @Operation(summary = "获取推荐", description = "基于向量相似度推荐相似海龟汤")
    public Result<Map<String, Double>> recommendSoups(
            @PathVariable String soupId,
            @Parameter(description = "推荐数量") @RequestParam(defaultValue = "5") int topK) {
        try {
            Map<String, Double> recommendations = turtleSoupService.recommendSoups(soupId, topK);
            return Result.success("推荐获取成功", recommendations);

        } catch (Exception e) {
            log.error("获取海龟汤推荐失败", e);
            return Result.error("推荐获取失败: " + e.getMessage());
        }
    }

    /**
     * 批量向量化现有海龟汤
     */
    @PostMapping("/batch-vectorize")
    @Operation(summary = "批量向量化", description = "对现有海龟汤进行批量向量化处理（仅需要核心字段，不需要用户信息）")
    public Result<String> batchVectorize(@RequestBody List<HaiGuiSoup> soups) {
        try {
            if (soups == null || soups.isEmpty()) {
                return Result.error("海龟汤列表不能为空");
            }

            int successCount = turtleSoupService.batchVectorizeSoups(soups);
            return Result.success(String.format("批量向量化完成: 成功 %d/%d", successCount, soups.size()));

        } catch (Exception e) {
            log.error("批量向量化失败", e);
            return Result.error("批量向量化失败: " + e.getMessage());
        }
    }

    /**
     * 简化的批量向量化接口（仅需要核心字段）
     */
    @PostMapping("/batch-vectorize-simple")
    @Operation(summary = "简化批量向量化", description = "使用简化的请求格式进行批量向量化，只需要核心字段")
    public Result<String> batchVectorizeSimple(@RequestBody List<SimpleSoupRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                return Result.error("请求列表不能为空");
            }

            // 转换为HaiGuiSoup对象
            List<HaiGuiSoup> soups = requests.stream()
                    .map(this::convertToHaiGuiSoup)
                    .collect(java.util.stream.Collectors.toList());

            int successCount = turtleSoupService.batchVectorizeSoups(soups);
            return Result.success(String.format("简化批量向量化完成: 成功 %d/%d", successCount, requests.size()));

        } catch (Exception e) {
            log.error("简化批量向量化失败", e);
            return Result.error("简化批量向量化失败: " + e.getMessage());
        }
    }



    /**
     * 转换简化请求为HaiGuiSoup对象
     */
    private HaiGuiSoup convertToHaiGuiSoup(SimpleSoupRequest request) {
        HaiGuiSoup soup = new HaiGuiSoup();
        soup.setSoupId(request.getSoupId());
        soup.setSoupTitle(request.getSoupTitle());
        soup.setSoupSurface(request.getSoupSurface());
        soup.setSoupBottom(request.getSoupBottom());
        soup.setHostManual(request.getHostManual());
        soup.setPlayCount(request.getPlayCount() != null ? request.getPlayCount() : 0);
        soup.setCreatedAt(request.getCreatedAt() != null ? request.getCreatedAt() : new java.util.Date());
        soup.setUpdatedAt(new java.util.Date());
        soup.setIsDeleted(false);
        return soup;
    }

    /**
     * 检查海龟汤向量化状态
     */
    @GetMapping("/vectorized/{soupId}")
    @Operation(summary = "检查向量化状态", description = "检查指定海龟汤是否已完成向量化")
    public Result<Boolean> checkVectorized(@PathVariable String soupId) {
        try {
            boolean isVectorized = turtleSoupService.isSoupVectorized(soupId);
            return Result.success("向量化状态检查完成", isVectorized);

        } catch (Exception e) {
            log.error("检查向量化状态失败", e);
            return Result.error("状态检查失败: " + e.getMessage());
        }
    }

    /**
     * 重新向量化指定海龟汤
     */
    @PostMapping("/re-vectorize/{soupId}")
    @Operation(summary = "重新向量化", description = "重新对指定海龟汤进行向量化处理")
    public Result<String> reVectorizeSoup(@PathVariable String soupId, @RequestBody HaiGuiSoup soup) {
        try {
            soup.setSoupId(soupId);
            boolean success = turtleSoupService.updateTurtleSoup(soup);
            if (success) {
                return Result.success("重新向量化完成", soupId);
            } else {
                return Result.error("重新向量化失败");
            }

        } catch (Exception e) {
            log.error("重新向量化失败", e);
            return Result.error("重新向量化失败: " + e.getMessage());
        }
    }
}