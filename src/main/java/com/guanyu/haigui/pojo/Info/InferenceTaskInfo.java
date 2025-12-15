package com.guanyu.haigui.pojo.Info;

import com.guanyu.haigui.converter.ListStringConverter;
import com.guanyu.haigui.converter.LongSetConverter;
import jakarta.persistence.Convert;
import lombok.Data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
@Data
public class InferenceTaskInfo {
    private String taskName;

    private String taskDescription;

    private Integer understandingLevel;

    @Convert(converter = ListStringConverter.class)
    private List<String> targetKeywords; // 移除初始化

    private String reasoningGoal;

    private Double progressWeight;

    private Boolean isMandatory = true;

    private Integer taskOrder = 0;

    // 关键修复：允许为null，在@PrePersist中初始化
    @Convert(converter = LongSetConverter.class) // 复用Long集合转换器
    private Set<Long> prerequisiteFragmentIds = new HashSet<>(); // 初始化空集合
}
