package com.guanyu.haigui.Deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.util.List;

/**
 * 自定义反序列化器：将字符串形式的JSON数组转换为List<Float>
 */
public class StringToListFloatDeserializer extends JsonDeserializer<List<Float>> {

    @Override
    public List<Float> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        // 1. 从JSON中获取字符串值（比如"[1.0,2.0,3.0]"）
        String jsonString = p.getText();
        // 2. 用ObjectMapper将字符串解析为List<Float>
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonString, new TypeReference<List<Float>>() {});
    }
}