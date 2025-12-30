package com.guanyu.haigui.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 线索片段DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClueFragmentDTO {

    /**
     * 线索片段ID
     */
    private Long fragmentId;

    /**
     * 海龟汤ID
     */
    private String soupId;

    /**
     * 线索片段内容
     */
    private String fragmentContent;


    /**
     * 关键词列表
     */
    private List<String> triggerKeywords;


    /**
     * 线索片段顺序
     */
    private Integer fragmentOrder;

    /**
     * 是否已删除
     */
    private Boolean isDeleted;
}