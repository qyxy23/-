package com.guanyu.haigui.pojo.result;

import com.guanyu.haigui.pojo.Info.ClueFragmentInfo;
import com.guanyu.haigui.pojo.Info.InferenceTaskInfo;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
@Data
@AllArgsConstructor
public class HaiGuiInfoResult {
    //主持人手册
    private String manual;
    //线索
    private List<ClueFragmentInfo> fragments;
    //进度任务
    private List<InferenceTaskInfo> inferenceTasks;
}
