package com.guanyu.haigui.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// 单文本请求（对应{"text":"xxx"}）
public class SingleEncodeRequest {
    private String text;
}