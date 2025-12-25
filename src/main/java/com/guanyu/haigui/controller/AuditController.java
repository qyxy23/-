package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.*;
import com.guanyu.haigui.pojo.result.HaiGuiDetailResult;
import com.guanyu.haigui.pojo.result.HaiGuiInfoResult;
import com.guanyu.haigui.pojo.vo.AddAuditUserVO;
import com.guanyu.haigui.pojo.vo.QueryMyTurtleSoupListVO;
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
    @PostMapping("/generateInfo/{auditId}")
    public Result<HaiGuiInfoResult> generateInfo(@PathVariable Long  auditId) {
        log.info("接收到信息生成请求");
        try {
            HaiGuiInfoResult result = auditService.generateInfo(auditId);
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

    @Operation(summary = "暂时保存用户的海龟汤", description = "暂时保存海龟汤")
    @PostMapping("/saveTurtleSoup")
    public Result<String> saveTurtleSoup(@RequestBody UpdateHaiGuiAuditDTO updateHaiGuiAuditDTO) {
        return Result.success(auditService.uploadHaiGuiAudit(updateHaiGuiAuditDTO));
    }


    @Operation(summary = "拒绝海龟汤接口", description = "审核员拒绝海龟汤并写明原因")
    @PostMapping("/rejectTurtleSoup")
    public Result<String> rejectTurtleSoup(@RequestBody rejectTurtleSoupDTO rejectTurtleSoupDTO) {
        return Result.success(auditService.rejectTurtleSoup(rejectTurtleSoupDTO));
    }

    @Operation(summary = "查询自己上传的海龟汤接口", description = "查看自己上传的海龟汤的审核状态")
    @PostMapping("/queryMyTurtleSoupList")
    public Result<QueryMyTurtleSoupListVO> queryMyTurtleSoupList(@RequestBody QueryTurtleSoupListDTO queryTurtleSoupListDTO) {
        return Result.success(auditService.queryMyTurtleSoupList(queryTurtleSoupListDTO));
    }
}
