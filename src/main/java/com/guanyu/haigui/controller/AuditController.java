package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.CreateTurtleSoupDTO;
import com.guanyu.haigui.pojo.dto.HaiGuiInfoGenerateDTO;
import com.guanyu.haigui.pojo.dto.QueryTurtleSoupListDTO;
import com.guanyu.haigui.pojo.result.HaiGuiDetailResult;
import com.guanyu.haigui.pojo.result.HaiGuiInfoResult;
import com.guanyu.haigui.pojo.vo.AddAuditUserVO;
import com.guanyu.haigui.pojo.vo.QueryTurtleSoupListVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.ServicesImpl.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "审核接口", description = "审核相关接口")
@RequiredArgsConstructor
@Slf4j
public class AuditController {
    private final AuditService auditService;

    @Operation(summary = "管理员添加审核用户")
    @PostMapping("/addAuditUser/{userId}")
    public AddAuditUserVO addAuditUser(@PathVariable Long userId) {
        return auditService.addAuditUser(userId);
    }

    @Operation(summary = "海龟汤相关信息生成接口", description = "根据汤面和汤底生成主持人手册，线索，进度任务")
    @PostMapping("/generateInfo")
    public Result<HaiGuiInfoResult> generateInfo(@RequestBody HaiGuiInfoGenerateDTO titleGenerateDTO) {
        log.info("接收到信息生成请求");
        try {
            HaiGuiInfoResult result = auditService.generateInfo(titleGenerateDTO);
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
        return Result.success(auditService.createTurtleSoup(createTurtleSoupDTO));
    }

    @Operation(summary = "查询用户提交的海龟汤列表", description = "查询用户提交的海龟汤列表")
    @PostMapping("/queryTurtleSoupList")
    public Result<QueryTurtleSoupListVO> queryTurtleSoupList(@RequestBody QueryTurtleSoupListDTO queryTurtleSoupListDTO) {
        return Result.success(auditService.queryTurtleSoupList(queryTurtleSoupListDTO));
    }

    @Operation(summary = "查询用户提交的海龟汤详细信息接口", description = "查询用户提交的海龟汤详细信息")
    @PostMapping("/queryTurtleSoupDetail/{auditId}")
    public Result<HaiGuiDetailResult> queryTurtleSoupDetail(@PathVariable Long auditId) {
        return Result.success(auditService.queryTurtleSoupDetail(auditId));
    }
}
