package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.GameEndReason;
import com.guanyu.haigui.pojo.result.GameSettlementSnapshot;
import lombok.Data;

import java.time.LocalDateTime;

/** 复盘构建时可复用的结算快照与汤面上下文，避免重复跑 SettlementBuilder */
@Data
public class ReplayBuildHints {
    private GameSettlementSnapshot snapshot;
    private String soupId;
    private String soupSurface;
    private LocalDateTime endTime;
    /** 对局结束原因（写入 Redis 复盘缓存） */
    private GameEndReason endReason;
}
