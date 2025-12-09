package com.guanyu.haigui.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Converter(autoApply = true)
@Slf4j
public class LongSetConverter implements AttributeConverter<Set<Long>, String> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Set<Long> attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    @Override
    public Set<Long> convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData, 
                objectMapper.getTypeFactory().constructCollectionType(Set.class, Long.class));
        } catch (IOException e) {
            log.warn("JSON 解析失败，返回空集合: {}", dbData);
            return new HashSet<>();
        }
    }
}