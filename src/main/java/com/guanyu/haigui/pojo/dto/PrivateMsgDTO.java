package com.guanyu.haigui.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PrivateMsgDTO {
    private String content;    // 消息内容
    private LocalDateTime time;// 消息时间
}