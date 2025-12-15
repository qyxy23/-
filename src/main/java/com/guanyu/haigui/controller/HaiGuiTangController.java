package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.*;
import com.guanyu.haigui.pojo.result.HaiGuiInfoResult;
import com.guanyu.haigui.pojo.vo.BatchEncodeResponse;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.pojo.vo.TitleGenerateResultVO;
import com.guanyu.haigui.pojo.vo.TurtleSoupEnhanceResultVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.ServicesImpl.HaiGuiTangServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@Tag(name = "海龟汤接口", description = "海龟汤接口")
public class HaiGuiTangController {
    private final HaiGuiTangServiceImpl haiGuiTangService;


    @Operation(summary = "向量化单个消息")
    @PostMapping("/vectorSignalTurtleSoup")
    public Result<SingleEncodeResponse> vectorSignalTurtleSoup(@RequestBody TurtleSoupSignalDTO content) {
        log.info("接收到的参数为：{}", content);
        return Result.success(haiGuiTangService.vectorSignalTurtleSoup(content.getContent()));
    }

    @Operation(summary = "向量化一系列消息")
    @PostMapping("/vectorTurtleSoup")
    public Result<BatchEncodeResponse> vectorTurtleSoup(@RequestBody TurtleSoupDTO content) {
        return Result.success(haiGuiTangService.vectorTurtleSoup(content.getContent()));
    }

    @Operation(summary = "海龟汤AI增强接口", description = "根据用户输入的海龟汤信息，调用AI生成完善的内容")
    @PostMapping("/enhanceTurtleSoup")
    public Result<TurtleSoupEnhanceResultVO> enhanceTurtleSoup(@RequestBody TurtleSoupEnhanceDTO enhanceDTO) {
        log.info("接收到海龟汤AI增强请求: {}", enhanceDTO.getSoupTitle());
        try {
            TurtleSoupEnhanceResultVO result = haiGuiTangService.enhanceTurtleSoup(enhanceDTO);
            log.info("海龟汤AI增强完成，状态: {}, prompt类型: {}", result.getStatus(), result.getPromptType());
            return Result.success(result);
        } catch (Exception e) {
            log.error("海龟汤AI增强失败", e);
            return Result.error("海龟汤AI增强失败: " + e.getMessage());
        }
    }

    @Operation(summary = "海龟汤标题生成接口", description = "根据汤面和汤底生成引人入胜的海龟汤标题")
    @PostMapping("/generateTitle")
    public Result<TitleGenerateResultVO> generateTitle(@RequestBody TitleGenerateDTO titleGenerateDTO) {
        log.info("接收到标题生成请求");
        try {
            TitleGenerateResultVO result = haiGuiTangService.generateTitle(titleGenerateDTO);
            log.info("标题生成完成: {}", result.getGeneratedTitle());
            return Result.success(result);
        } catch (Exception e) {
            log.error("标题生成失败", e);
            return Result.error("标题生成失败: " + e.getMessage());
        }
    }

    @Operation(summary = "海龟汤相关信息生成接口", description = "根据汤面和汤底生成主持人手册，线索，进度任务")
    @PostMapping("/generateInfo")
    public Result<HaiGuiInfoResult> generateInfo(@RequestBody HaiGuiInfoGenerateDTO titleGenerateDTO) {
        log.info("接收到信息生成请求");
        try {
            HaiGuiInfoResult result = haiGuiTangService.generateInfo(titleGenerateDTO);
            log.info("信息生成完成: {}", result);
            return Result.success(result);
        } catch (Exception e) {
            log.error("信息生成失败", e);
            return Result.error("信息生成失败: " + e.getMessage());
        }
    }

    @Operation(summary = "审核员创建海龟汤接口", description = "审核员导入海龟汤")
    @PostMapping("/createTurtleSoup")
    public Result<String> createTurtleSoup(@RequestBody CreateTurtleSoupDTO createTurtleSoupDTO) {
        return Result.success(haiGuiTangService.createTurtleSoup(createTurtleSoupDTO));
    }
}
