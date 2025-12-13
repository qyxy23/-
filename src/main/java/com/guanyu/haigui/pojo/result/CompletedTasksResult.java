package com.guanyu.haigui.pojo.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CompletedTasksResult {
    private Long taskId;
    private String taskName;
    private String description;
    private LocalDateTime completionTime;
}