package com.guanyu.haigui.pojo.result;

import lombok.Data;

import java.util.List;
@Data
public class CompletedCluesResult {
    //片段ID
    private Long fragmentId;

    //片段内容
    private String fragmentContent;


    //触发关键词
    private List<String> triggerKeywords;
}
