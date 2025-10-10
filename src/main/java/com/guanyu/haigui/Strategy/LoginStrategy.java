package com.guanyu.haigui.Strategy;

import com.guanyu.haigui.pojo.vo.CustomUserDetails ;

import javax.security.sasl.AuthenticationException;
import java.util.Map;

public interface LoginStrategy {
    /**
     * 执行登录验证
     * @param params 登录参数（从LoginRequest中获取）
     * @return 认证结果
     * @throws AuthenticationException 认证失败异常
     */
    CustomUserDetails  login(Map<String, String> params) throws AuthenticationException;
}