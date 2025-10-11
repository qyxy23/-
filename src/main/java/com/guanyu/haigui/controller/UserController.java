package com.guanyu.haigui.controller;

import com.guanyu.haigui.Enum.LoginType;
import com.guanyu.haigui.Enum.RegisterType;
import com.guanyu.haigui.Strategy.RegisterStrategy;
import com.guanyu.haigui.pojo.dto.RegisterRequest;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.Strategy.LoginStrategy;
import com.guanyu.haigui.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.security.sasl.AuthenticationException;
import java.util.Map;

@Slf4j
@RestController
@Tag(name = "登录接口", description = "用户登录注册相关接口")
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
    @PostMapping("/login")
    @PreAuthorize("hasAnyRole('USER')")
    public Result<CustomUserDetails> Login(LoginType type, Map<String, String> params)
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

    @GetMapping("/test")
    public Result<String> test() {
        return Result.success("Hello World");
    }


    /**
     * 执行注册
     *
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result<CustomUserDetails> register(
            @RequestBody RegisterRequest request) throws Exception {
        log.info("进入 register 方法");
        System.out.println("进入 register 方法");
        System.out.println("request: " + request);
        RegisterStrategy strategy = RegisterstrategyMap.get(request.getType());

        if (strategy == null) {
            return Result.error("注册方式不存在");
        }

        return Result.success(strategy.register(request));
    }
}