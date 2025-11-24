package com.guanyu.haigui.pojo.vo;

import lombok.Data;
import java.util.List;

/**
 * 海龟汤AI增强结果VO
 * 返回AI生成的完善信息，可直接插入数据库
 */
@Data
public class TurtleSoupEnhanceResultVO {
    /**
     * AI生成的进度设置列表（JSON格式）
     * 格式：[{"taskName":"任务名称","description":"任务描述","increment":进度增量}]
     */
    private String progressSettings;

    /**
     * AI生成的关键线索列表（JSON格式）
     * 格式：[{"content":"线索内容","isKey":true,"clueType":"类型","difficulty":1}]
     */
    private String keyClues;

    /**
     * AI生成的主持人手册
     */
    private String hostManual;

    /**
     * 生成状态信息
     */
    private String status;

    /**
     * 使用的prompt类型（用于调试）
     */
    private String promptType;
}