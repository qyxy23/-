package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum CoverReportReason {
    PORNOGRAPHY("色情低俗"),
    VIOLENCE("血腥暴力"),
    ADVERTISING("广告引流"),
    COPYRIGHT("侵权"),
    OTHER("其他");

    private final String label;

    CoverReportReason(String label) {
        this.label = label;
    }
}
