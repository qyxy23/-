package com.guanyu.haigui.Exception;

public class UserAlreadyExistsException extends BusinessException {
    public UserAlreadyExistsException(String username) {
        super("用户名 '" + username + "' 已被注册");
    }
}