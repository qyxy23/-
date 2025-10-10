package com.guanyu.haigui.Expection;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}