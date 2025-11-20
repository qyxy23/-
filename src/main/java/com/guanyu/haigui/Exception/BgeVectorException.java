package com.guanyu.haigui.Exception;

import lombok.Getter;

@Getter
public class BgeVectorException extends RuntimeException {
    // Getter方法
    private int statusCode; // 服务端状态码
    private String response; // 服务端响应体

    // 构造器（包含状态码和响应体）
    public BgeVectorException(String message, int statusCode, String response) {
        super(message);
        this.statusCode = statusCode;
        this.response = response;
    }

    // 简化构造器（用于包装其他异常）
    public BgeVectorException(String message, Throwable cause) {
        super(message, cause);
        if (cause instanceof BgeVectorException) {
            this.statusCode = ((BgeVectorException) cause).statusCode;
            this.response = ((BgeVectorException) cause).response;
        }
    }

}