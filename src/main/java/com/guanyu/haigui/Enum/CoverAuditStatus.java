package com.guanyu.haigui.Enum;

/**
 * 海龟汤封面上传审核状态（仅上传者封面）
 */
public enum CoverAuditStatus {
    /** 无待审封面 */
    NONE,
    /** 机器审疑似，待人工复核 */
    PENDING_REVIEW,
    /** 最近一次上传被机器拒绝（展示提示后可再传） */
    REJECTED
}
