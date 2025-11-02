package com.guanyu.haigui.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MessageInfo {
    private String content;
    private LocalDateTime time;
}