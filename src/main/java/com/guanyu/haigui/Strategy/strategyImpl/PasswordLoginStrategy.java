package com.guanyu.haigui.Strategy.strategyImpl;

import com.guanyu.haigui.mapper.UserDetailsMapper;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import com.guanyu.haigui.Strategy.LoginStrategy;
import com.guanyu.haigui.utils.JwtTokenUtil;
import com.guanyu.haigui.utils.RedisServiceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.security.sasl.AuthenticationException;
import java.util.Collection;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PasswordLoginStrategy implements LoginStrategy {

    @Resource
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtUtil; // JWT工具类（生成Token）
    private final UserDetailsMapper loginUserDetailsMapper;

    @Resource
    private RedisServiceUtil redisServiceUtil;

    @Override
    public CustomUserDetails login(Map<String, String> params) throws AuthenticationException {
        String username = params.get("username");
        String password = params.get("password");

        // 1. 验证账号密码（Spring Security的AuthenticationManager）
        try {
            // 1. Spring Security 认证（已验证账号密码有效性）
            Authentication authentication = new UsernamePasswordAuthenticationToken(username, password);
            Authentication authenticated = authenticationManager.authenticate(authentication);

            // 2. 提取用户角色（从 Spring Security 的认证信息中获取，最准确）
            Collection<? extends GrantedAuthority> authorities = authenticated.getAuthorities();
            String role = authorities.stream()
                    .map(GrantedAuthority::getAuthority).toString(); // 转换为角色字符串（如 "ROLE_ADMIN"）

            // 3. 获取用户信息（用于返回 VO）
            UserInfo userInfo = loginUserDetailsMapper.selectUserInfoByUsername(username);
            // 3.1 生成【带角色信息】的 JWT Token
            String token = jwtUtil.generateToken(userInfo.getId(), role);
            CustomUserDetails CustomUserDetails = new CustomUserDetails();
            BeanUtils.copyProperties(userInfo, CustomUserDetails);
            CustomUserDetails.setToken(token);
            CustomUserDetails.setRole(role);

            // 4. 只在Redis上更新用户在线状态
            redisServiceUtil.updateOnlineStatus(userInfo.getId(), token);

            return CustomUserDetails;
        } catch (BadCredentialsException e) {
            throw new AuthenticationException("密码错误");
        } catch (UsernameNotFoundException e) {
            throw new AuthenticationException("用户不存在");
        }
    }
}