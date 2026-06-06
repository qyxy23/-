package com.guanyu.haigui.pojo.result;

import com.guanyu.haigui.pojo.Info.ClueFragmentInfo;
import com.guanyu.haigui.pojo.Info.InferenceTaskInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HaiGuiInfoResult {
    /** 主持人手册（供真人主持参考） */
    private String manual;
    /** AI 判题规则（边界条件，线上判题使用） */
    private String aiJudgeRules;
    private List<ClueFragmentInfo> fragments;
    private List<InferenceTaskInfo> inferenceTasks;
}
