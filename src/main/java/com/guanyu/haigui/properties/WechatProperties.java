package com.guanyu.haigui.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "qingyou.wechat")
@Data
public class WechatProperties {

    private String appid;
    private String secret;
    /** 微信开放平台移动应用 AppID（App 端绑定/登录，可选） */
    private String mobileAppid;
    private String mobileSecret;
}
