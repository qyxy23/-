package com.guanyu.haigui.pojo.result;

import lombok.Data;


@Data
public class UncompletedTasksResult {
    private Long taskId;
    private String taskName;
    private String description;
}
