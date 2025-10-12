package com.guanyu.haigui.Strategy.strategyImpl;

import com.guanyu.haigui.pojo.dto.LoginRequest;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import com.guanyu.haigui.Strategy.LoginStrategy;
import com.guanyu.haigui.pojo.vo.LogVO;
import com.guanyu.haigui.utils.JwtTokenUtil;
import com.guanyu.haigui.utils.RedisServiceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import javax.security.sasl.AuthenticationException;

@Component
@RequiredArgsConstructor
public class PasswordLoginStrategy implements LoginStrategy {

    @Resource
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtUtil; // JWT工具类（生成Token）
    @Resource
    private RedisServiceUtil redisServiceUtil;

    @Override
    public LogVO login(LoginRequest request) throws AuthenticationException {
        String username = request.getUsername();
        String password = request.getPassword();

        // 1. 验证账号密码（Spring Security的AuthenticationManager）
        try {
            // 1. Spring Security 认证（已验证账号密码有效性）
            Authentication authentication = new UsernamePasswordAuthenticationToken(username, password);
            Authentication authenticated = authenticationManager.authenticate(authentication);

            // 2. 获取用户信息（用于返回 VO）
            CustomUserDetails customUserDetails = (CustomUserDetails) authenticated.getPrincipal();
            // 3 生成【带角色信息】的 JWT Token
            String token = jwtUtil.generateToken(customUserDetails);
            customUserDetails.setToken(token);

            // 4. 只在Redis上更新用户在线状态
            redisServiceUtil.updateOnlineStatus(customUserDetails.getId(), token);
            LogVO loginVO = new LogVO();
            BeanUtils.copyProperties(customUserDetails, loginVO);
            System.out.println("LoginVO authorities AFTER copy: " + loginVO.getAuthorities());
            return loginVO;
        } catch (BadCredentialsException e) {
            throw new AuthenticationException("密码错误");
        } catch (UsernameNotFoundException e) {
            throw new AuthenticationException("用户不存在");
        }
    }
}