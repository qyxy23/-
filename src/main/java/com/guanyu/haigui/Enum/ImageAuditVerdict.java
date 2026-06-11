package com.guanyu.haigui.Enum;

/**
 * 数据万象图片审核 Result：0 正常，1 违规，2 疑似
 */
public enum ImageAuditVerdict {
    PASS,
    REJECT,
    SUSPECT;

    public static ImageAuditVerdict fromCiResult(String result) {
        if ("0".equals(result)) {
            return PASS;
        }
        if ("2".equals(result)) {
            return SUSPECT;
        }
        return REJECT;
    }
}
