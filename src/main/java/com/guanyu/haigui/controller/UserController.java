package com.guanyu.haigui.controller;

import com.guanyu.haigui.Enum.LoginType;
import com.guanyu.haigui.Enum.RegisterType;
import com.guanyu.haigui.Strategy.LoginStrategy;
import com.guanyu.haigui.Strategy.RegisterStrategy;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.*;
import com.guanyu.haigui.pojo.vo.LogVO;
import com.guanyu.haigui.pojo.vo.UserInfoVO;
import com.guanyu.haigui.pojo.vo.otherInfoVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.security.sasl.AuthenticationException;
import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@Tag(name = "登录接口", description = "用户登录注册相关接口")
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {
    @Resource
    private final Map<LoginType, LoginStrategy> strategyMap; // 策略映射（Spring自动注入所有实现）
    private final UserService userService;
    @Resource
    private final Map<RegisterType, RegisterStrategy> RegisterstrategyMap; // 策略映射（Spring自动注入所有实现）


    @PostMapping("/bindPhone")
    @Operation(summary = "绑定手机号码")
    public Result<String> bindPhone(@RequestBody BindPhoneRequest request) {
        return Result.success(userService.bindPhone(request.getPhone()));
    }

    @PostMapping("/bindEmail")
    @Operation(summary = "绑定邮箱")
    public Result<String> bindEmail(@RequestBody BindEmailRequest request) {
        return Result.success(userService.bindEmail(request.getEmail()));
    }

    @PostMapping("/bindPassword")
    @Operation(summary = "修改密码", description = "需校验旧密码后更新为新密码")
    public Result<String> changePassword(@RequestBody BindPasswordRequest request) {
        return Result.success(userService.changePassword(request.getOldPassword(), request.getNewPassword()));
    }


    /**
     * 上传用户头像接口
     * @param avatarFile 头像文件（表单参数，name=avatar）
     * @return 头像访问URL或错误信息
     */
    @Operation(summary = "上传用户头像")
    @PostMapping("/upAvatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("avatar") MultipartFile avatarFile) {
        try {
            String avatarUrl = userService.uploadUserAvatar(avatarFile);
            return ResponseEntity.ok(Collections.singletonMap("avatarUrl", avatarUrl));
        } catch (IllegalArgumentException e) {
            // 参数错误（如文件过大、类型不对）
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        } catch (RuntimeException e) {
            // 系统错误（如上传失败、用户不存在）
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "服务器内部错误，请稍后重试"));
        }
    }


    /**
     * 执行登录验证
     *
     * @return 认证结果
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LogVO> Login(@RequestBody LoginRequest request)
            throws AuthenticationException {
        LoginStrategy strategy = strategyMap.get(request.getType());
        if (strategy == null) {
            return Result.error("登录方式不存在");
        }
        return Result.success(strategy.login(request));
    }

    // 退出登录接口
    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String token) {
        return Result.success(userService.logout(token));
    }


    @Operation(summary = "测试")
    @GetMapping("/test")
    public Result<String> test() {
        return Result.success("Hello World");
    }


    /**
     * 执行注册
     *
     * @return 注册结果
     */
    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<LogVO> register(
            @RequestBody RegisterRequest request) throws Exception {
        RegisterStrategy strategy = RegisterstrategyMap.get(request.getType());

        if (strategy == null) {
            return Result.error("注册方式不存在");
        }

        return Result.success(strategy.register(request));
    }

    @Operation(summary = "获取用户信息")
    @GetMapping("/info")
    public Result<UserInfoVO> getUserInfo() {
        return Result.success(userService.getUserInfo(BaseContext.getCurrentId()));
    }

    @Operation(summary = "获取其他用户信息")
    @GetMapping("/otherInfo/{userId}")
    public Result<otherInfoVO> getOtherInfo(@PathVariable Long userId) {
        return Result.success(userService.getOtherInfo(userId));
    }
}