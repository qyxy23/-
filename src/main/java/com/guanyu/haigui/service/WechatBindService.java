package com.guanyu.haigui.service;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.guanyu.haigui.Enum.WechatBindPlatform;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.mapper.UserDetailsMapper;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.WechatOAuthTokenVO;
import com.guanyu.haigui.pojo.vo.WechatSessionVO;
import com.guanyu.haigui.properties.WechatProperties;
import com.guanyu.haigui.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatBindService {

    private static final String OAUTH2_URL =
            "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code";

    private final WechatMiniProgramService wechatMiniProgramService;
    private final WechatProperties wechatProperties;
    private final UserInfoRepository userInfoRepository;
    private final UserDetailsMapper userDetailsMapper;

    @Transactional(rollbackFor = Exception.class)
    public String bindWechat(String code, WechatBindPlatform platform) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(400, "微信授权 code 不能为空");
        }
        Long userId = BaseContext.getCurrentId();
        UserInfo current = userInfoRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        String openid;
        String unionid;
        if (platform == WechatBindPlatform.APP) {
            WechatOAuthTokenVO oauth = appOAuth2(code);
            openid = oauth.getOpenid();
            unionid = oauth.getUnionid();
        } else {
            WechatSessionVO session = wechatMiniProgramService.code2Session(code);
            openid = session.getOpenid();
            unionid = session.getUnionid();
        }

        assertWechatNotBoundByOthers(userId, openid, unionid);

        if (platform == WechatBindPlatform.MP_WEIXIN) {
            current.setWxOpenid(openid);
        }
        if (StringUtils.hasText(unionid)) {
            current.setWxUnionid(unionid);
        } else if (platform == WechatBindPlatform.APP && !StringUtils.hasText(current.getWxOpenid())) {
            throw new BusinessException(400, "未获取到 unionid，请确认小程序已绑定微信开放平台，或在小程序内绑定微信");
        }
        userInfoRepository.save(current);
        log.info("用户[{}]绑定微信成功 platform={} openid={}", userId, platform, openid);
        return "微信绑定成功";
    }

    private WechatOAuthTokenVO appOAuth2(String code) {
        if (!StringUtils.hasText(wechatProperties.getMobileAppid())
                || !StringUtils.hasText(wechatProperties.getMobileSecret())) {
            throw new BusinessException(400, "App 微信绑定未配置，请在小程序【设置】中绑定，或联系管理员配置移动应用 AppID");
        }
        String url = String.format(
                OAUTH2_URL,
                wechatProperties.getMobileAppid(),
                wechatProperties.getMobileSecret(),
                code
        );
        String body = HttpUtil.get(url);
        JSONObject json = JSONUtil.parseObj(body);
        WechatOAuthTokenVO token = json.toBean(WechatOAuthTokenVO.class);
        if (token.getErrcode() != null && token.getErrcode() != 0) {
            throw new BusinessException(401, "App 微信授权失败：" + token.getErrmsg());
        }
        if (!StringUtils.hasText(token.getOpenid())) {
            throw new BusinessException(401, "App 微信授权失败：未获取 openid");
        }
        return token;
    }

    private void assertWechatNotBoundByOthers(Long userId, String openid, String unionid) {
        if (StringUtils.hasText(openid)) {
            UserInfo byOpenid = userDetailsMapper.selectUserInfoByWxOpenid(openid);
            if (byOpenid != null && !byOpenid.getUserId().equals(userId)) {
                throw new BusinessException(409, "该微信已绑定其他账号");
            }
        }
        if (StringUtils.hasText(unionid)) {
            UserInfo byUnionid = userDetailsMapper.selectUserInfoByWxUnionid(unionid);
            if (byUnionid != null && !byUnionid.getUserId().equals(userId)) {
                throw new BusinessException(409, "该微信已绑定其他账号");
            }
        }
    }

    public boolean isWechatBound(UserInfo user) {
        return user != null && (StringUtils.hasText(user.getWxOpenid()) || StringUtils.hasText(user.getWxUnionid()));
    }
}
