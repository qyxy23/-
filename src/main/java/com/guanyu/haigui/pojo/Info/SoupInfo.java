package com.guanyu.haigui.pojo.Info;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 海龟汤信息类（运行时判题用）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SoupInfo {
    private String soupId;
    private String soupTitle;
    private String soupSurface;
    private String soupBottom;
    private String aiJudgeRules;
    private Double currentProgress;
}
