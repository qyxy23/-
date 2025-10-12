package com.guanyu.haigui.Enum;

import lombok.Getter;
import org.apache.ibatis.type.Alias;

/**
 * 消息发送者类型枚举（对应数据库sender_type字段）
 */
@Getter
@Alias("SenderTypeEnum") // MyBatis别名
public enum SenderTypeEnum {
    USER("用户"),
    AI("AI"),
    SYSTEM("系统");

    private final String desc;

    SenderTypeEnum(String desc) {
        this.desc = desc;
    }

    /**
     * 用于MyBatis-Plus枚举与数据库的映射（返回枚举名，如"USER"/"AI"）
     */
    public String getValue() {
        return this.name();
    }
}