package com.guanyu.haigui.Strategy;

import com.guanyu.haigui.pojo.dto.LoginRequest;
import com.guanyu.haigui.pojo.vo.CustomUserDetails ;

import javax.security.sasl.AuthenticationException;

public interface LoginStrategy {
    /**
     * 执行登录验证
     * @param request 登录参数
     * @return 认证结果
     * @throws AuthenticationException 认证失败异常
     */
    CustomUserDetails login(LoginRequest request) throws AuthenticationException;
}