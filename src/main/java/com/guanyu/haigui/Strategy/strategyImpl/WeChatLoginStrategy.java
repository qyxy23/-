package com.guanyu.haigui.Strategy.strategyImpl;

import com.guanyu.haigui.Enum.UserRoleEnum;
import com.guanyu.haigui.Strategy.LoginStrategy;
import com.guanyu.haigui.mapper.UserDetailsMapper;
import com.guanyu.haigui.pojo.dto.LoginRequest;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.model.UserRole;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import com.guanyu.haigui.pojo.vo.LogVO;
import com.guanyu.haigui.pojo.vo.WechatSessionVO;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.PlayQuotaService;
import com.guanyu.haigui.service.WechatMiniProgramService;
import com.guanyu.haigui.service.WechatProfileSyncService;
import com.guanyu.haigui.utils.JwtTokenUtil;
import com.guanyu.haigui.utils.RedisServiceUtil;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.security.sasl.AuthenticationException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatLoginStrategy implements LoginStrategy {

    private final WechatMiniProgramService wechatMiniProgramService;
    private final JwtTokenUtil jwtUtil;
    @Resource
    private RedisServiceUtil redisServiceUtil;
    @Resource
    private UserDetailsMapper userDetailsMapper;
    @Resource
    private PlayQuotaService playQuotaService;
    @Resource
    private UserInfoRepository userInfoRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogVO login(LoginRequest request) throws AuthenticationException {
        WechatSessionVO session = wechatMiniProgramService.code2Session(request.getCode());
        String openid = session.getOpenid();
        String unionid = session.getUnionid();

        UserInfo existing = userDetailsMapper.selectUserInfoByWxOpenid(openid);
        if (existing != null && isAppPasswordAccount(existing)) {
            throw new AuthenticationException("该微信已绑定 App 账号，请使用账号密码登录");
        }

        CustomUserDetails userDetails;
        if (existing != null) {
            userDetails = buildUserDetails(existing);
        } else {
            userDetails = registerWechatUser(openid, unionid);
        }

        UserInfo profileUser = userInfoRepository.findById(userDetails.getUserId()).orElse(null);

        String token = jwtUtil.generateToken(userDetails);
        userDetails.setToken(token);
        redisServiceUtil.updateOnlineStatus(userDetails.getUserId(), token);

        LogVO loginVO = new LogVO();
        BeanUtils.copyProperties(userDetails, loginVO);
        loginVO.setToken(token);
        loginVO.setAuthorities(userDetails.getAuthorities());
        loginVO.setNeedsProfileSetup(WechatProfileSyncService.needsProfileSetup(profileUser));
        return loginVO;
    }

    /** App 账号密码注册的用户（含手动绑定微信），不走小程序一键登录 */
    private boolean isAppPasswordAccount(UserInfo user) {
        return user != null && StringUtils.hasText(user.getPassword());
    }

    private CustomUserDetails registerWechatUser(String openid, String unionid) throws AuthenticationException {
        String username = buildUniqueUsername(openid);
        CustomUserDetails newUser = new CustomUserDetails();
        newUser.setUsername(username);
        newUser.setWxOpenid(openid);
        if (StringUtils.hasText(unionid)) {
            newUser.setWxUnionid(unionid);
        }

        int insertCount = userDetailsMapper.insertWechatUser(newUser);
        if (insertCount <= 0 || newUser.getUserId() == null) {
            throw new AuthenticationException("微信用户注册失败");
        }

        UserRoleEnum registerRole = UserRoleEnum.USER;
        UserRole userRole = new UserRole();
        userRole.setUserId(newUser.getUserId());
        userRole.setRoleId(registerRole.getRoleId());
        if (userDetailsMapper.insertUserRole(userRole) <= 0) {
            throw new AuthenticationException("微信用户角色关联失败");
        }
        playQuotaService.initForNewUser(newUser.getUserId());

        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + registerRole.getRoleCode())
        );
        newUser.setAuthorities(authorities);
        log.info("微信新用户注册成功 openid={} userId={} tempUsername={}", openid, newUser.getUserId(), username);
        return newUser;
    }

    private CustomUserDetails buildUserDetails(UserInfo userInfo) {
        List<String> roleNames = userDetailsMapper.selectUserRolesByUserId(userInfo.getUserId());
        List<SimpleGrantedAuthority> authorities = roleNames.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        CustomUserDetails details = new CustomUserDetails();
        BeanUtils.copyProperties(userInfo, details);
        details.setAuthorities(authorities);
        return details;
    }

    private String buildUniqueUsername(String openid) {
        String suffix = openid.length() > 10 ? openid.substring(openid.length() - 10) : openid;
        String base = "wx_" + suffix.replaceAll("[^a-zA-Z0-9]", "");
        String candidate = base;
        int i = 0;
        while (userDetailsMapper.selectUserInfoByUsername(candidate) != null) {
            i++;
            candidate = base + i;
        }
        return candidate;
    }
}
