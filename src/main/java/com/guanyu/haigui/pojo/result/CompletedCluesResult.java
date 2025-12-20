package com.guanyu.haigui.pojo.result;

import com.guanyu.haigui.Enum.ClueType;
import lombok.Data;

import java.util.List;
@Data
public class CompletedCluesResult {
    //片段ID
    private Long fragmentId;

    //片段内容
    private String fragmentContent;

    //片段类型
    private ClueType fragmentType; // 类型改为ClueType枚举

    //难度
    private Integer difficulty = 2;

    //重要性
    private Integer importance = 5;

    //触发关键词
    private List<String> triggerKeywords;

    //是否为关键线索
    private Boolean isCoreClue = false;

    //片段顺序
    private Integer fragmentOrder = 0;
}
