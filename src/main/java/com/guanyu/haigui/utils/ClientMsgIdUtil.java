package com.guanyu.haigui.utils;

import org.springframework.util.StringUtils;

/** 客户端消息 ID 规范化（发送幂等） */
public final class ClientMsgIdUtil {

    private static final int MAX_LENGTH = 64;

    private ClientMsgIdUtil() {
    }

    /** 空白或超长返回 null，表示不参与幂等 */
    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String id = raw.trim();
        if (id.length() > MAX_LENGTH) {
            return null;
        }
        return id;
    }

}
