package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guanyu.haigui.Enum.TheorySubmissionStatus;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmitTheoryVO {

    private TheorySubmissionStatus status;

    private Double coverageScore;

    private List<String> missingTasks;

    private String message;

    private Integer remainingFormalAttempts;

    private TheoryUnlockVO theoryUnlock;

    private EndGameVO endGame;
}
