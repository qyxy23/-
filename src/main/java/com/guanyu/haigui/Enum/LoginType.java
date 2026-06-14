package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum LoginType {
    PASSWORD,  // 账号密码
    SMS,       // 手机号验证码
    WECHAT     // 微信小程序
}