package com.guanyu.haigui.pojo.result;

import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.model.InferenceTask;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
@Data
@AllArgsConstructor
public class HaiGuiInfoResult {
    //主持人手册
    private String manual;
    private List<ClueFragment> fragments;
    private List<InferenceTask> inferenceTasks;
}
