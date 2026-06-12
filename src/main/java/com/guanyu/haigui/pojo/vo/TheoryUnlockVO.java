package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TheoryUnlockVO {

    private Boolean theorySubmitEnabled;

    private String lockReason;

    private Double unlockProgressRequired;

    private Double winProgressRequired;

    private Integer remainingFormalAttempts;

    private Integer questionCount;

    private Integer triggeredClueCount;

    private Integer completedTaskCount;

    private Double currentProgress;
}
