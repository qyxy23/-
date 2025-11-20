package com.guanyu.haigui.pojo.model;

import lombok.Data;

import java.util.Map;
@Data
public class HaiGuiSoupEntry {
    private String soupId; // 汤面唯一ID（如UUID）
    private String soupSurface; // 汤面文本
    private String soupBottom; // 汤底文本
    private String hostManual; // 主持人手册
    private String keyClues; // 关键线索
    private String progressSettings; // 进度设置
    private Map<String, float[]> embeddings; // 各模块的向量（汤面、汤底、主持人手册等）

    // 构造器、Getter/Setter省略
}