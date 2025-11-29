package com.guanyu.haigui.pojo.Content;

import com.guanyu.haigui.pojo.model.ClueFragment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 增强内容数据类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TurtleSoupEnhancedContent {
    private String soupTitle;
    private String soupSurface;
    private String soupBottom;
    private List<ClueFragment> clueFragments;
    private List<Map<String, Object>> inferenceTasks;
    private String generationStrategy;
    private long generatedAt;
}