package com.guanyu.haigui.controller;

import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.pojo.dto.UploadHaiGuiSoupDTO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.ServicesImpl.TurtleSoupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;

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



    @Operation(summary = "上传海龟汤图片", description = "上传海龟汤图片")
    @PostMapping("/uploadHaiGuiSoupAvatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("avatar") MultipartFile avatarFile,
                                          @RequestParam("soupId") String soupId) {
        try{
            String avatarUrl = turtleSoupService.uploadHaiGuiSoupAvatar(avatarFile,soupId);
            return ResponseEntity.ok(Collections.singletonMap("avatarUrl", avatarUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "服务器内部错误，请稍后重试"));
        }
    }
}