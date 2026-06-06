package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.*;
import com.guanyu.haigui.pojo.result.HaiGuiDetailResult;
import com.guanyu.haigui.pojo.vo.AddAuditUserVO;
import com.guanyu.haigui.pojo.vo.GenerateInfoResponseVO;
import com.guanyu.haigui.pojo.vo.PublishResponseVO;
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
    public Result<GenerateInfoResponseVO> generateInfo(@PathVariable Long auditId) {
        log.info("接收到 AI 生成提交请求 auditId={}", auditId);
        try {
            GenerateInfoResponseVO result = auditService.submitGenerateInfo(auditId);
            log.info("AI 生成已提交 auditId={}, status={}", auditId, result.getStatus());
            return Result.success(result);
        } catch (Exception e) {
            log.error("AI 生成提交失败 auditId={}", auditId, e);
            return Result.error("AI 生成提交失败: " + e.getMessage());
        }
    }

    @Operation(summary = "审核员发布海龟汤", description = "提交异步发布，向量化入库在后台完成")
    @PostMapping("/createTurtleSoup")
    public Result<PublishResponseVO> createTurtleSoup(@RequestBody CreateTurtleSoupDTO createTurtleSoupDTO) {
        log.info("接收到发布提交请求 auditId={}", createTurtleSoupDTO.getAuditRecordId());
        PublishResponseVO result = auditService.submitPublish(createTurtleSoupDTO);
        log.info("发布已提交 auditId={}, status={}", createTurtleSoupDTO.getAuditRecordId(), result.getStatus());
        return Result.success(result);
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


    @Operation(summary = "上传者修改自己的海龟汤", description = "待审核或已拒绝可修改，通过后不可改")
    @PostMapping("/updateMyTurtleSoup")
    public Result<String> updateMyTurtleSoup(@RequestBody UpdateHaiGuiAuditDTO updateHaiGuiAuditDTO) {
        return Result.success(auditService.updateMyTurtleSoup(updateHaiGuiAuditDTO));
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
