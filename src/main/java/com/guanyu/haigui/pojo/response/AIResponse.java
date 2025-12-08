package com.guanyu.haigui.pojo.response;

import lombok.Data;

import java.util.List;

@Data
public class AIResponse {
    private String rawResponse;
    private String answer; // 是/不是/是或不是/不重要
    private List<Long> newTriggeredFragments; // 新触发的线索ID
    private List<Long> completedTasks; // 新完成的任务ID
    private List<Long> updatedTriggeredIds; // 更新后的所有线索ID
    private String reason; // AI判断理由
}