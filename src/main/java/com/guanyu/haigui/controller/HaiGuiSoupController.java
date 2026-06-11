package com.guanyu.haigui.controller;

import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.pojo.dto.HandleCoverReportDTO;
import com.guanyu.haigui.pojo.dto.QueryCoverReportListDTO;
import com.guanyu.haigui.pojo.dto.SubmitCoverReportDTO;
import com.guanyu.haigui.pojo.dto.UploadHaiGuiSoupDTO;
import com.guanyu.haigui.pojo.vo.CoverReportItemVO;
import com.guanyu.haigui.pojo.vo.CoverReportListVO;
import com.guanyu.haigui.pojo.vo.SoupCoverUploadVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.pojo.dto.HandleSoupContentReportDTO;
import com.guanyu.haigui.pojo.dto.QuerySoupReportListDTO;
import com.guanyu.haigui.pojo.dto.SubmitSoupContentReportDTO;
import com.guanyu.haigui.pojo.vo.SoupContentReportItemVO;
import com.guanyu.haigui.pojo.vo.SoupContentReportListVO;
import com.guanyu.haigui.service.ServicesImpl.SoupContentReportService;
import com.guanyu.haigui.service.ServicesImpl.SoupCoverReportService;
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
import java.util.HashMap;
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
    private final SoupCoverReportService soupCoverReportService;
    private final SoupContentReportService soupContentReportService;

    @Operation(summary = "用户上传海龟汤", description = "用户上传海龟汤，供审核员进行审核")
    @PostMapping("/upload")
    public Result<String> uploadSoup(@RequestBody UploadHaiGuiSoupDTO soup) {
        return Result.success(turtleSoupService.uploadTurtleSoup(soup));
    }

    @Operation(summary = "上传海龟汤封面", description = "审核员直传生效；上传者走机器审，疑似进人工复核")
    @PostMapping("/uploadHaiGuiSoupAvatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("avatar") MultipartFile avatarFile,
                                          @RequestParam("soupId") String soupId) {
        try {
            SoupCoverUploadVO vo = turtleSoupService.uploadHaiGuiSoupAvatar(avatarFile, soupId);
            return ResponseEntity.ok(toCoverResponse(vo));
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

    @Operation(summary = "审核员通过待复核封面")
    @PostMapping("/approveSoupCover/{soupId}")
    public Result<SoupCoverUploadVO> approveSoupCover(@PathVariable String soupId) {
        return Result.success(turtleSoupService.approvePendingCover(soupId));
    }

    @Operation(summary = "审核员拒绝待复核封面")
    @PostMapping("/rejectSoupCover/{soupId}")
    public Result<SoupCoverUploadVO> rejectSoupCover(@PathVariable String soupId,
                                                     @RequestParam(required = false) String reason) {
        return Result.success(turtleSoupService.rejectPendingCover(soupId, reason));
    }

    @Operation(summary = "举报海龟汤封面")
    @PostMapping("/reportCover")
    public Result<CoverReportItemVO> reportCover(@RequestBody SubmitCoverReportDTO dto) {
        return Result.success(soupCoverReportService.submitReport(dto));
    }

    @Operation(summary = "审核员查询封面举报列表")
    @PostMapping("/coverReport/list")
    public Result<CoverReportListVO> listCoverReports(@RequestBody QueryCoverReportListDTO dto) {
        return Result.success(soupCoverReportService.listReports(dto));
    }

    @Operation(summary = "审核员处理封面举报")
    @PostMapping("/coverReport/handle")
    public Result<CoverReportItemVO> handleCoverReport(@RequestBody HandleCoverReportDTO dto) {
        return Result.success(soupCoverReportService.handleReport(dto));
    }

    @Operation(summary = "举报海龟汤内容")
    @PostMapping("/reportSoup")
    public Result<SoupContentReportItemVO> reportSoup(@RequestBody SubmitSoupContentReportDTO dto) {
        return Result.success(soupContentReportService.submitReport(dto));
    }

    @Operation(summary = "审核员查询海龟汤举报列表")
    @PostMapping("/soupReport/list")
    public Result<SoupContentReportListVO> listSoupReports(@RequestBody QuerySoupReportListDTO dto) {
        return Result.success(soupContentReportService.listReports(dto));
    }

    @Operation(summary = "审核员处理海龟汤举报")
    @PostMapping("/soupReport/handle")
    public Result<SoupContentReportItemVO> handleSoupReport(@RequestBody HandleSoupContentReportDTO dto) {
        return Result.success(soupContentReportService.handleReport(dto));
    }

    @Operation(summary = "我的封面举报列表")
    @PostMapping("/myReport/cover/list")
    public Result<CoverReportListVO> listMyCoverReports(@RequestBody QueryCoverReportListDTO dto) {
        return Result.success(soupCoverReportService.listMyReports(dto));
    }

    @Operation(summary = "撤回封面举报")
    @PostMapping("/myReport/cover/withdraw/{reportId}")
    public Result<CoverReportItemVO> withdrawCoverReport(@PathVariable Long reportId) {
        return Result.success(soupCoverReportService.withdrawMyReport(reportId));
    }

    @Operation(summary = "我的海龟汤举报列表")
    @PostMapping("/myReport/soup/list")
    public Result<SoupContentReportListVO> listMySoupReports(@RequestBody QuerySoupReportListDTO dto) {
        return Result.success(soupContentReportService.listMyReports(dto));
    }

    @Operation(summary = "撤回海龟汤举报")
    @PostMapping("/myReport/soup/withdraw/{reportId}")
    public Result<SoupContentReportItemVO> withdrawSoupReport(@PathVariable Long reportId) {
        return Result.success(soupContentReportService.withdrawMyReport(reportId));
    }

    private Map<String, Object> toCoverResponse(SoupCoverUploadVO vo) {
        Map<String, Object> body = new HashMap<>();
        body.put("avatarUrl", vo.getAvatarUrl());
        body.put("pendingCoverUrl", vo.getPendingCoverUrl());
        body.put("coverAuditStatus", vo.getCoverAuditStatus() != null ? vo.getCoverAuditStatus().name() : "NONE");
        body.put("message", vo.getMessage());
        return body;
    }
}
