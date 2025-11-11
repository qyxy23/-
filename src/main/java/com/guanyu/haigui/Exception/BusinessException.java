package com.guanyu.haigui.Exception;

public class BusinessException extends RuntimeException {
    public BusinessException(int i, String message) {
        super(message);
    }
}