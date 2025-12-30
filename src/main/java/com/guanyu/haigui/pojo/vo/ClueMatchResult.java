package com.guanyu.haigui.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 线索匹配结果
 * 用于在海龟汤中搜索相关线索的返回结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClueMatchResult {

    /**
     * 片段ID
     */
    private String fragmentId;

    /**
     * 片段内容（线索文本）
     */
    private String fragmentContent;

    /**
     * 匹配原因说明
     */
    private String matchReason;
}