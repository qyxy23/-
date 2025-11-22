package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class CreateHaiGuiSoupDTO {
    // 标题
    private String soupTitle;

    // 汤
    private String soupSurface;

    // 底
    private String soupBottom;

    // 主持人手册
    private String hostManual;

    // 汤的线索
    private String keyClues; // 或用自定义类型（如JsonType）映射JSON数组

    // 汤的进度设置
    private String progressSettings; // 或用自定义类型映射JSON对象
}
