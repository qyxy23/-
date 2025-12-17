package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.CreateHaiGuiSoupDTO;
import com.guanyu.haigui.pojo.dto.UploadHaiGuiSoupDTO;
import com.guanyu.haigui.pojo.vo.ClueMatchResult;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.ServicesImpl.TurtleSoupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

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

    @Operation(summary = "用户上传海龟汤", description = "用户上传海龟汤，供审核员进行审核")
    @PostMapping("/upload")
    public Result<String> uploadSoup(@RequestBody UploadHaiGuiSoupDTO soup) {
        return Result.success(turtleSoupService.uploadTurtleSoup(soup));
    }

    /**
     * 创建海龟汤（包含向量化）
     */
    @PostMapping("/create")
    @Operation(summary = "创建海龟汤", description = "创建新的海龟汤并自动向量化存储")
    public Result<String> createSoup(@RequestBody CreateHaiGuiSoupDTO soup) {
        try {
            log.info("接收到创建海龟汤请求:");
            log.info("  soupTitle: '{}'", soup.getSoupTitle());
            log.info("  soupSurface: '{}'", soup.getSoupSurface());
            log.info("  soupBottom: '{}'", soup.getSoupBottom());
            log.info("  hostManual: '{}'", soup.getHostManual());
            log.info("  estimatedDuration: '{}分钟'", soup.getEstimatedDuration());
            log.info("  playerCount: '{}人'", soup.getPlayerCount());
            log.info("  difficultyLevel: '{}'", soup.getDifficultyLevel());
            log.info("  tag: '{}'", soup.getTag());
            log.info("  keyClues类型: {}, 值: '{}'",
                    soup.getKeyClues() != null ? soup.getKeyClues().getClass().getSimpleName() : "null",
                    soup.getKeyClues());
            log.info("  progressSettings类型: {}, 值: '{}'",
                    soup.getProgressSettings() != null ? soup.getProgressSettings().getClass().getSimpleName() : "null",
                    soup.getProgressSettings());

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
     * 在指定海龟汤中搜索相关线索
     */
    @PostMapping("/search-clues")
    @Operation(summary = "搜索汤内线索", description = "在指定海龟汤中基于向量匹配搜索相关线索")
    public Result<List<ClueMatchResult>> searchCluesInSoup(
            @Parameter(description = "玩家问题") @RequestParam String question,
            @Parameter(description = "海龟汤ID") @RequestParam String soupId,
            @Parameter(description = "返回线索数量") @RequestParam(defaultValue = "10") int topK) {
        try {
            log.info("搜索汤内线索请求: question={}, soupId={}, topK={}", question, soupId, topK);

            List<ClueMatchResult> results = turtleSoupService.findMatchingCluesInSoup(question, soupId, topK);
            return Result.success("线索搜索完成", results);

        } catch (Exception e) {
            log.error("搜索汤内线索失败: question={}, soupId={}", question, soupId, e);
            return Result.error("线索搜索失败: " + e.getMessage());
        }
    }



    @Operation(summary = "上传海龟汤图片", description = "上传海龟汤图片")
    @PostMapping("/uploadHaiGuiSoupAvatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("avatar") MultipartFile avatarFile,
                                          @RequestParam("soupId") String soupId) {
        try{
            String avatarUrl = turtleSoupService.uploadHaiGuiSoupAvatar(avatarFile,soupId);
            return ResponseEntity.ok(Collections.singletonMap("avatarUrl", avatarUrl));
        } catch (IllegalArgumentException e) {
            // 参数错误（如文件过大、类型不对）
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        } catch (RuntimeException e) {
            // 系统错误（如上传失败、用户不存在）
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "服务器内部错误，请稍后重试"));
        }
    }
}