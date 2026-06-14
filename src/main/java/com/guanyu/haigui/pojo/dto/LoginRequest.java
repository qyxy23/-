package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.LoginType;
import com.guanyu.haigui.pojo.model.UserInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class LoginRequest extends UserInfo implements Serializable {
    private LoginType type;
    /** 微信小程序 wx.login 返回的 code */
    private String code;
}