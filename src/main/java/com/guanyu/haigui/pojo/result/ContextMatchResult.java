package com.guanyu.haigui.pojo.result;

import com.guanyu.haigui.Enum.VectorType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上下文匹配结果类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContextMatchResult {
    private String id;          // 可以是soupId、clueId等
    private String content;     // 原始文本内容
    private Double similarity;  // 相似度分数
    private VectorType type;    // 向量类型
}