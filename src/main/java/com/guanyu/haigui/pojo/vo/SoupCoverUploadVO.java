package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.CoverAuditStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SoupCoverUploadVO {
    /** 当前对外展示的封面 URL */
    private String avatarUrl;
    /** 待人工复核的封面 URL（有值时 avatarUrl 仍为旧封面） */
    private String pendingCoverUrl;
    private CoverAuditStatus coverAuditStatus;
    private String message;
    /** AI 生成封面时返回，便于审核员核对 Prompt */
    private String promptUsed;
}
