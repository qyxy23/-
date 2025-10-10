package com.guanyu.haigui.Hanldler;

import com.guanyu.haigui.Exception.UserAlreadyExistsException;
import com.guanyu.haigui.result.Result;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;


/**
 * 全局异常处理器
 */
@ControllerAdvice
public class SecurityExceptionHandler {

    /**
     * 登录失败处理
     */
    @ExceptionHandler(BadCredentialsException.class)
    public Result<?> handleBadCredentials(BadCredentialsException ex) {
        return Result.error("用户名或密码错误");
    }

    /**
     * 注册失败处理
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public Result<?> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return Result.error(ex.getMessage()); // 返回类似 "用户名 'admin' 已被注册"
    }

    @ExceptionHandler(DisabledException.class)
    public Result<?> handleDisabledAccount(DisabledException ex) {
        return Result.error("账户已被禁用，请联系管理员");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDenied(AccessDeniedException ex) {
        return Result.error("无权限访问该资源");
    }
}
