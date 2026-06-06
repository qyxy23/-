package com.guanyu.haigui.pojo.Info;

import com.guanyu.haigui.converter.ListLongConverter;
import jakarta.persistence.Convert;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InferenceTaskInfo {
    private String taskName;
    private String taskDescription;
    private String reasoningGoal;
    private Double progressWeight;
    private Integer taskOrder = 0;

    @Convert(converter = ListLongConverter.class)
    private List<Long> prerequisiteFragmentIds = new ArrayList<>();
}
