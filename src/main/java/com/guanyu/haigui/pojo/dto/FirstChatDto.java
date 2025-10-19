package com.guanyu.haigui.pojo.dto;

import lombok.Data;

// ChatRequest.java（请求体DTO）
@Data // Lombok 注解，自动生成getter/setter
public class FirstChatDto {
    private String message; // 与前端传参的字段名保持一致
}