package com.guanyu.haigui.pojo.result;

import com.guanyu.haigui.pojo.model.ClueFragment;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 拆解结果类，包含线索片段和推理任务
 */
@Data
@AllArgsConstructor
public class DecompositionResult {
    private List<ClueFragment> fragments;
    private List<Map<String, Object>> inferenceTasks;

}