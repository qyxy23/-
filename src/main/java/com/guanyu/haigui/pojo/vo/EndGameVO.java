package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.MessageChatType;
import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.result.TaskResult;
import lombok.Data;

import java.util.List;

@Data
public class EndGameVO {
    private String roomId;
    private String soupBottom;
    private RoomStatus status;
    private MessageChatType type;
    private double currentProgress;  // 完成进度百分比
    private int finalScore;          // 最终得分(0-100)
    private List<TaskResult> completedTasks;
    private List<TaskResult> uncompletedTasks;
    private int totalTasks;           // 总任务数
    private List<String> triggeredClues; // 触发的线索ID列表
}