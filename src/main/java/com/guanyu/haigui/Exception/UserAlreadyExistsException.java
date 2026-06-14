package com.guanyu.haigui.Exception;

public class UserAlreadyExistsException extends BusinessException {
    public UserAlreadyExistsException(String username) {
        super(403, "该用户名已被使用，请换一个");
    }
}