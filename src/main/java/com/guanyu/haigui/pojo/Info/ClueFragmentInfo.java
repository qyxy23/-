package com.guanyu.haigui.pojo.Info;

import com.guanyu.haigui.converter.ListStringConverter;
import jakarta.persistence.Convert;
import lombok.Data;

import java.util.List;

@Data
public class ClueFragmentInfo {
    // 只保留表中存在的字段
    private String content;

    @Convert(converter = ListStringConverter.class)
    private List<String> triggerKeywords;
}