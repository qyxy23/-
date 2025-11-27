package com.guanyu.haigui.pojo.vo;

import lombok.Data;

/**
 * 标题生成结果VO
 */
@Data
public class TitleGenerateResultVO {

    /**
     * 生成的标题
     */
    private String generatedTitle;

    /**
     * 生成状态
     */
    private String status;

    /**
     * 标题类型（original 原标题/optimized ai生成的标题）
     */
    private String titleType;

    /**
     * 生成说明或建议
     */
    private String suggestion;
}