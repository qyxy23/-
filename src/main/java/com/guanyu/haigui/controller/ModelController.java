package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.ModelCreateRequest;
import com.guanyu.haigui.pojo.dto.ModelStatusUpdateRequest;
import com.guanyu.haigui.pojo.response.ModelResponse;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.ServicesImpl.ModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "大模型接口", description = "大模型相关接口")
@RestController
@RequestMapping("/models")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ModelController {
    private final ModelService modelService;

    // 获取所有模型配置
    @GetMapping
    @Operation(summary = "获取所有模型配置")
    public Result<List<ModelResponse>> getAllModels() {
        return Result.success(modelService.getAllModels());
    }

    // 创建新模型配置
    @PostMapping
    @Operation(summary = "创建新模型配置")
    public Result<ModelResponse> createModel(@Valid @RequestBody ModelCreateRequest request) {
        ModelResponse response = modelService.createModel(request);
        return Result.success(response);
    }

    // 删除模型配置
    @DeleteMapping("/{id}")
    @Operation(summary = "删除模型配置")
    public Result<Void> deleteModel(@PathVariable Long id) {
        modelService.deleteModel(id);
        return Result.success();
    }

    // 启用/禁用模型
    @PostMapping("/{id}/status")
    @Operation(summary = "启用/禁用模型")
    public Result<ModelResponse> toggleModelStatus(
            @PathVariable Long id,
            @Valid @RequestBody ModelStatusUpdateRequest request) {
        ModelResponse response = modelService.updateModelStatus(id, request.getIsActive());
        return Result.success(response);
    }


    /**
     * 获取当前正在使用的大模型信息
     * 顺序：先查Redis，若为空则查数据库启用中的第一个
     */
    @GetMapping("/current")
    @Operation(summary = "获取当前正在使用中的模型信息")
    public Result<ModelResponse> getCurrentModel() {
        ModelResponse currentModel = modelService.getCurrentModel();

        if (currentModel == null) {
            return Result.error("当前没有启用的模型"); // 或者返回错误信息
        }

        return Result.success(currentModel);
    }

    @Operation(summary = "更换所使用的大模型")
    @PostMapping("/change/{id}")
    public Result<ModelResponse> changeModel(@Valid @PathVariable Long id) {
        ModelResponse response = modelService.updateCurModelStatus(id);
        return Result.success(response);
    }
}