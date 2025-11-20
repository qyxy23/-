package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.guanyu.haigui.Deserializer.StringToListFloatDeserializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 单文本响应（对应{"texts":[...]}）
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SingleEncodeResponse {
    /**
     * 对应服务端返回的"texts"字段：原始文本字符串
     */
    private String texts;

    /**
     * 对应服务端返回的"embeddings"字段：二维浮点向量数组（每个子数组是一个文本的向量）
     */
    @JsonDeserialize(using = StringToListFloatDeserializer.class)
    @JsonProperty("embeddings")
    private List<List<Float>> embeddings;
}