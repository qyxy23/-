package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum WechatBindPlatform {
    /** 微信小程序 wx.login */
    MP_WEIXIN,
    /** App 微信 Open SDK（需配置移动应用 AppID） */
    APP
}
