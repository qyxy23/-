package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.LoginType;
import lombok.Data;

import java.util.Map;

@Data
public class LoginRequest {
    private LoginType type;       // 登录类型
    private Map<String, String> params; // 动态参数（不同方式取不同key）
}