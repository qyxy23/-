package com.guanyu.haigui.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 批量文本请求（假设服务支持{"texts":["a","b"]}）
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchEncodeRequest {
    private List<String> text;
}