package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class StartSoloVO {
    private String gameSessionId;
    private String soupId;
    private String soupTitle;
    private String soupSurface;
    private Integer remainingQuestions;
    private Double progress;
    /** true=恢复进行中的会话，false=新开一局 */
    private Boolean resumed;
}
