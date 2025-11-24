package com.guanyu.haigui.pojo.dto;

import lombok.Data;

/**
 * 标题生成DTO
 * 基于汤面和汤底生成海龟汤标题
 */
@Data
public class TitleGenerateDTO {

    /**
     * 汤面（故事背景）
     */
    private String soupSurface;

    /**
     * 汤底（真相解答）
     */
    private String soupBottom;

    /**
     * 当前标题（可选，用于优化）
     */
    private String currentTitle;
}