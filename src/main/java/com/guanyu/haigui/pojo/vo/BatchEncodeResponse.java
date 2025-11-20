package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 批量文本响应（假设服务返回{"embeddings":[[...],[...]]}）
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchEncodeResponse {
    /**
     * 对应服务端返回的"texts"字段：原始文本字符串
     */
    private List<String> texts;

    @JsonProperty("embeddings")
    private List<List<Float>> embeddings;
}