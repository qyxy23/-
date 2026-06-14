package com.guanyu.haigui.pojo.vo;

import lombok.Data;

@Data
public class WechatSessionVO {
    private String openid;
    private String sessionKey;
    private String unionid;
    private Integer errcode;
    private String errmsg;
}
