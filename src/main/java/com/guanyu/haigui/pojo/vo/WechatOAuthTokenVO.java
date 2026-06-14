package com.guanyu.haigui.pojo.vo;

import lombok.Data;

@Data
public class WechatOAuthTokenVO {
    private String openid;
    private String unionid;
    private Integer errcode;
    private String errmsg;
}
