package com.guanyu.haigui.Hanldler;

import com.guanyu.haigui.Exception.*;
import com.guanyu.haigui.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

import javax.security.sasl.AuthenticationException;


/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    /**
     * 405 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<String> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body("请求方法不被支持: " + ex.getMessage());
    }

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

    @ExceptionHandler(NoBeginRequest.class)
    public Result<?> handleNoBeginRequest(NoBeginRequest ex) {
        return Result.error(ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDenied(AccessDeniedException ex) {
        return Result.error("无权限访问该资源");
    }

    @ExceptionHandler(RoomException.class)
    public Result<?> handleAccessDenied(RoomException ex) {
        return Result.error(ex.getMessage());
    }


    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException ex) {
        ex.printStackTrace();
        return Result.error(405, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception ex) {
        ex.printStackTrace();
        return Result.error(ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public Result<?> handleThrowable(Throwable ex) {
        return Result.error(ex.getMessage());
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public Result<String> handleRoomNotFound(RoomNotFoundException e) {
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(UserNotInRoomException.class)
    public Result<String> handleUserNotInRoom(UserNotInRoomException e) {
        return Result.error(e.getMessage());

    }

    @ExceptionHandler(RoomFullException.class)
    public Result<String> handleRoomFull(RoomFullException e) {
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(TokenErrorException.class)
    public Result<String> handleTokenError(TokenErrorException e) {
        return Result.error(401,e.getMessage());
    }

    @ExceptionHandler(FriendsException.class)
    public Result<String> handleFriendsException(FriendsException e) {
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusinessException(BusinessException e) {
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public Result<String> handleUnauthorizedException(UnauthorizedException e) {
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<String> handleMultipartException(MultipartException e) {
        log.error("Multipart request parsing failed: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body("文件上传请求格式错误，请确保使用multipart/form-data格式");
    }


}
