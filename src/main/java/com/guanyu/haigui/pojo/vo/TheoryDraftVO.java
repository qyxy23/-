package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class TheoryDraftVO {
    private String draftText;
    private Integer draftVersion;
    private Long editorUserId;
    private String editorUsername;
    private LocalDateTime lockExpiresAt;
    /** 当前用户是否持锁可编辑 */
    private Boolean canEdit;
    /** 当前用户是否可抢锁（无投票进行中、无他人有效锁） */
    private Boolean canAcquireLock;
    /** 是否有人正在编辑（含自己） */
    private Boolean editing;
}
