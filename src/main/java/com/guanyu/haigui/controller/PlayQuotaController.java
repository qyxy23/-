package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.GrantPlayQuotaDTO;
import com.guanyu.haigui.pojo.dto.QueryPlayAccessRequestDTO;
import com.guanyu.haigui.pojo.dto.ReviewPlayAccessRequestDTO;
import com.guanyu.haigui.pojo.dto.SubmitPlayAccessRequestDTO;
import com.guanyu.haigui.pojo.vo.PlayAccessRequestListVO;
import com.guanyu.haigui.pojo.vo.PlayAccessRequestVO;
import com.guanyu.haigui.pojo.vo.PlayQuotaSummaryVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.PlayQuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "游玩额度", description = "额度查询、申请与审核")
@RequiredArgsConstructor
public class PlayQuotaController {

    private final PlayQuotaService playQuotaService;

    @Operation(summary = "查询我的游玩额度")
    @PostMapping("/play/quota/me")
    public Result<PlayQuotaSummaryVO> getMyQuota() {
        return Result.success(playQuotaService.getMySummary());
    }

    @Operation(summary = "提交游玩额度申请")
    @PostMapping("/play/access/submit")
    public Result<PlayAccessRequestVO> submitAccessRequest(@RequestBody SubmitPlayAccessRequestDTO dto) {
        return Result.success(playQuotaService.submitAccessRequest(dto));
    }

    @Operation(summary = "我的申请记录")
    @PostMapping("/play/access/my")
    public Result<List<PlayAccessRequestVO>> listMyRequests() {
        return Result.success(playQuotaService.listMyRequests());
    }

    @Operation(summary = "审核员查询申请列表")
    @PostMapping("/play/access/list")
    public Result<PlayAccessRequestListVO> listForReview(@RequestBody QueryPlayAccessRequestDTO dto) {
        return Result.success(playQuotaService.listAccessRequestsForReview(dto));
    }

    @Operation(summary = "审核员处理申请")
    @PostMapping("/play/access/review")
    public Result<PlayAccessRequestVO> review(@RequestBody ReviewPlayAccessRequestDTO dto) {
        return Result.success(playQuotaService.reviewAccessRequest(dto));
    }

    @Operation(summary = "管理员直接赠送额度")
    @PostMapping("/play/quota/grant")
    public Result<String> adminGrant(@RequestBody GrantPlayQuotaDTO dto) {
        playQuotaService.adminGrantQuota(dto);
        return Result.success("赠送成功");
    }
}
