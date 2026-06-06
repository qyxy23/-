package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class BindPasswordRequest {
    private String oldPassword;
    private String newPassword;
}
