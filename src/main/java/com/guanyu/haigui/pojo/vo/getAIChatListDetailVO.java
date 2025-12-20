package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.pojo.result.CompletedCluesResult;
import com.guanyu.haigui.pojo.result.CompletedTasksResult;
import com.guanyu.haigui.pojo.result.UncompletedTasksResult;
import lombok.Data;

import java.util.List;

@Data
public class getAIChatListDetailVO {
    private String roomId;
    // 汤标题
    private String soupTitle;
    // 汤面
    private String soupSurface;
    // 汤底
    private String soupBottom;
    // 汤进度
    private double currentProgress;
    // 最终得分(0-100)
    private int finalScore;
    // 已完成任务
    private List<CompletedTasksResult> completedTasks;
    // 未完成任务
    private List<UncompletedTasksResult> uncompletedTasks;
    // 总任务数
    private int totalTasks;
    // 完成的线索
    private List<CompletedCluesResult> completedClues;
    // 未完成的线索
    private List<CompletedCluesResult> uncompletedClues;
    // 问题
    private List<RoomGetClueVO.QuestionClass> question;
}
