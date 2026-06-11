package com.guanyu.haigui.Exception;

import lombok.Getter;

@Getter
public class PlayQuotaException extends RuntimeException {

    public static final int CODE_QUOTA_EXHAUSTED = 40201;
    public static final int CODE_PENDING_REQUEST = 40202;

    private final int code;

    public PlayQuotaException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static PlayQuotaException exhausted(String message) {
        return new PlayQuotaException(CODE_QUOTA_EXHAUSTED, message);
    }

    public static PlayQuotaException pendingRequest(String message) {
        return new PlayQuotaException(CODE_PENDING_REQUEST, message);
    }
}
