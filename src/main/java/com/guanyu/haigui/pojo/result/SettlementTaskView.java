package com.guanyu.haigui.pojo.result;

import lombok.Data;

/** 结算页展示用任务摘要（不含内部 ID） */
@Data
public class SettlementTaskView {
    private String taskName;
    private String description;
}
