package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.TurtleSoupDTO;
import com.guanyu.haigui.pojo.dto.TurtleSoupSignalDTO;
import com.guanyu.haigui.pojo.vo.BatchEncodeResponse;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.ServicesImpl.haiGuiTangServiceImpl;
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
    private final haiGuiTangServiceImpl haiGuiTangService;

    @Operation(summary = "调用ai生成相对应的主持人手册模块")
    @PostMapping("/generateHostManual")
    public Result<String> generateHostManual(@RequestBody String content) {
        return Result.success(haiGuiTangService.generateHostManual(content));
    }

    @Operation(summary = "调用ai生成相对应的关键线索模块")
    @PostMapping("/generateKeyClue")
    public Result<String> generateKeyClue(@RequestBody String content) {
        return Result.success(haiGuiTangService.generateKeyClue(content));
    }

    @Operation(summary = "调用ai生成相对应的进度设置模块")
    @PostMapping("/generateProgressSetting")
    public Result<String> generateProgressSetting(@RequestBody String content) {
        return Result.success(haiGuiTangService.generateProgressSetting(content));
    }

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
}
