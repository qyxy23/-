package com.guanyu.haigui.service;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.pojo.vo.WechatSessionVO;
import com.guanyu.haigui.properties.WechatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatMiniProgramService {

    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    private final WechatProperties wechatProperties;

    public WechatSessionVO code2Session(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(400, "微信登录 code 不能为空");
        }
        if (!StringUtils.hasText(wechatProperties.getAppid()) || !StringUtils.hasText(wechatProperties.getSecret())) {
            throw new BusinessException(500, "微信小程序 appid/secret 未配置");
        }
        String url = String.format(
                CODE2SESSION_URL,
                wechatProperties.getAppid(),
                wechatProperties.getSecret(),
                code
        );
        String body = HttpUtil.get(url);
        JSONObject json = JSONUtil.parseObj(body);
        WechatSessionVO session = json.toBean(WechatSessionVO.class);
        if (session.getErrcode() != null && session.getErrcode() != 0) {
            log.warn("微信 code2session 失败: {}", body);
            throw new BusinessException(401, "微信登录失败：" + (session.getErrmsg() != null ? session.getErrmsg() : "invalid code"));
        }
        if (!StringUtils.hasText(session.getOpenid())) {
            throw new BusinessException(401, "微信登录失败：未获取 openid");
        }
        return session;
    }
}
