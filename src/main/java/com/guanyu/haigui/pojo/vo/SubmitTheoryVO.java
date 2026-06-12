package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guanyu.haigui.Enum.TheoryPartialReason;
import com.guanyu.haigui.Enum.TheorySubmissionStatus;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmitTheoryVO {

    private TheorySubmissionStatus status;

    private Double coverageScore;

    private List<String> missingTasks;

    /** 灰区进度不足时的盘汤指引（建议追问句） */
    private List<String> hints;

    /** PARTIAL 时的主要原因 */
    private TheoryPartialReason partialReason;

    /** 距判胜进度还差多少百分点（仅 PROGRESS_GAP 时有值） */
    private Double progressGap;

    private String message;

    private Integer remainingFormalAttempts;

    private TheoryUnlockVO theoryUnlock;

    private EndGameVO endGame;
}
