package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.StartSoloRequest;
import com.guanyu.haigui.pojo.dto.SubmitTheoryRequest;
import com.guanyu.haigui.pojo.vo.EndGameVO;
import com.guanyu.haigui.pojo.vo.OngoingSoloVO;
import com.guanyu.haigui.pojo.vo.RoomGetClueVO;
import com.guanyu.haigui.pojo.vo.StartSoloVO;
import com.guanyu.haigui.pojo.vo.SubmitTheoryVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.SoloGameService;
import com.guanyu.haigui.service.TheorySubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "单人游戏", description = "单人 AI 海龟汤玩法")
@RestController
@AllArgsConstructor
@RequestMapping("/solo")
public class SoloGameController {

    private final SoloGameService soloGameService;
    private final TheorySubmissionService theorySubmissionService;

    @Operation(summary = "开始或恢复单人游戏")
    @PostMapping("/start")
    public Result<StartSoloVO> start(@RequestBody StartSoloRequest request) {
        if (request == null || !StringUtils.hasText(request.getSoupId())) {
            return Result.error("海龟汤 ID 不能为空");
        }
        return Result.success(soloGameService.startSolo(request.getSoupId()));
    }

    @Operation(summary = "进行中的单人游戏列表")
    @PostMapping("/ongoing")
    public Result<java.util.List<OngoingSoloVO>> listOngoing() {
        return Result.success(soloGameService.listOngoing());
    }

    @Operation(summary = "获取单人游戏状态")
    @PostMapping("/clue/{gameSessionId}")
    public Result<RoomGetClueVO> getState(@PathVariable String gameSessionId) {
        return Result.success(soloGameService.getSoloState(gameSessionId));
    }

    @Operation(summary = "主动放弃")
    @PostMapping("/giveUp/{gameSessionId}")
    public Result<EndGameVO> giveUp(@PathVariable String gameSessionId) {
        return Result.success(soloGameService.giveUp(gameSessionId));
    }

    @Operation(summary = "获取单人结算（游戏已结束后）")
    @PostMapping("/settlement/{gameSessionId}")
    public Result<EndGameVO> getSettlement(@PathVariable String gameSessionId) {
        return Result.success(soloGameService.getSettlement(gameSessionId));
    }

    @Operation(summary = "提交汤底推理")
    @PostMapping("/submitTheory")
    public Result<SubmitTheoryVO> submitTheory(@RequestBody SubmitTheoryRequest request) {
        if (request == null || !StringUtils.hasText(request.getGameSessionId())) {
            return Result.error("游戏会话 ID 不能为空");
        }
        if (!StringUtils.hasText(request.getTheory())) {
            return Result.error("推理内容不能为空");
        }
        return Result.success(theorySubmissionService.submitTheory(
                request.getGameSessionId().trim(),
                request.getTheory()));
    }
}
