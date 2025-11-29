package com.guanyu.haigui.pojo.Info;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 海龟汤信息类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SoupInfo {
    private String soupId;
    private String soupTitle;
    private String soupSurface;
    private String soupBottom;
    private String hostManual;
    private Double currentProgress;

    public SoupInfo(String soupId, String soupTitle, String soupSurface, String soupBottom, String hostManual) {
        this.soupId = soupId;
        this.soupTitle = soupTitle;
        this.soupSurface = soupSurface;
        this.soupBottom = soupBottom;
        this.hostManual = hostManual;
    }
}