package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class OngoingSoloVO {
    private String gameSessionId;
    private String soupId;
    private String soupTitle;
    private String soupSurface;
    private Integer remainingQuestions;
    private Double progress;
    private LocalDateTime startTime;
}
