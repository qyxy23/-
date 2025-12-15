package com.guanyu.haigui.converter;

import com.guanyu.haigui.Enum.SoupTag;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)  // 自动生效
public class SoupTagConverter implements AttributeConverter<SoupTag, String> {

    @Override
    public String convertToDatabaseColumn(SoupTag tag) {
        return tag == null ? null : tag.getDescription();  // 存储 description 值
    }

    @Override
    public SoupTag convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return null;
        return SoupTag.fromString(dbValue);  // 通过数据库值反向查找枚举
    }
}