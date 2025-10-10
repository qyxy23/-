package com.guanyu.haigui.controller;

import com.guanyu.haigui.Enum.LoginType;
import com.guanyu.haigui.Enum.RegisterType;
import com.guanyu.haigui.Strategy.RegisterStrategy;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.Strategy.LoginStrategy;
import com.guanyu.haigui.service.UserService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.security.sasl.AuthenticationException;
import java.util.Map;

@RestController
@Api(tags = "登录接口")
@Component
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {
    @Resource
    @Qualifier("strategyMap")
    private final Map<LoginType, LoginStrategy> strategyMap; // 策略映射（Spring自动注入所有实现）
    private final UserService userService;
    @Resource
    @Qualifier("registerStrategyMap")
    private final Map<RegisterType, RegisterStrategy> RegisterstrategyMap; // 策略映射（Spring自动注入所有实现）

    /**
     * 执行登录
     *
     * @param type   登录类型
     * @param params 登录参数
     * @return 认证结果
     */
    @RequestMapping("/doLogin")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public Result<CustomUserDetails> executeLogin(LoginType type, Map<String, String> params)
            throws AuthenticationException {
        LoginStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            return Result.error("登录方式不存在");
        }
        return Result.success(strategy.login(params));
    }

    // 退出登录接口
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String token) {
        return Result.success(userService.logout(token));
    }

    /**
     * 执行注册
     *
     * @param type   注册类型
     * @param params 注册参数
     * @return 注册结果
     */
    @RequestMapping("/logup")
    @PreAuthorize("hasAnyRole('USER')")
    public Result<CustomUserDetails> register(RegisterType type, Map<String, String> params) throws Exception {
        RegisterStrategy strategy = RegisterstrategyMap.get(type);
        if (strategy == null) {
            return Result.error("登录方式不存在");
        }
        return Result.success(strategy.register(params));
    }
}