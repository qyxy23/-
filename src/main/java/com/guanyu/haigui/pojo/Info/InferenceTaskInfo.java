package com.guanyu.haigui.pojo.Info;

import com.guanyu.haigui.converter.ListLongConverter;
import com.guanyu.haigui.converter.ListStringConverter;
import jakarta.persistence.Convert;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InferenceTaskInfo {
    // 只保留表中存在的字段
    private String taskName;
    private String taskDescription;

    @Convert(converter = ListStringConverter.class)
    private List<String> targetKeywords;

    private String reasoningGoal;

    // 使用Double或BigDecimal都可以，根据实体类决定
    private Double progressWeight;

    private Integer taskOrder = 0;

    // 注意：这里使用List<Long>表示前置线索ID列表
    @Convert(converter = ListLongConverter.class)
    private List<Long> prerequisiteFragmentIds = new ArrayList<>();
}