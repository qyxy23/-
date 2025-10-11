package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.RegisterType;
import lombok.Data;

@Data
public class RegisterRequest {
    private RegisterType type;
    private String username;
    private String password;
}