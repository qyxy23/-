package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.WechatBindPlatform;
import lombok.Data;

@Data
public class BindWechatRequest {
    private String code;
    /** 默认小程序 */
    private WechatBindPlatform platform = WechatBindPlatform.MP_WEIXIN;
}
