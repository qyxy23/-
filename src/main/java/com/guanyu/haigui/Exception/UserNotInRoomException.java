package com.guanyu.haigui.Exception;

public class UserNotInRoomException extends RuntimeException {
    public UserNotInRoomException(String message) { super(message); }
}