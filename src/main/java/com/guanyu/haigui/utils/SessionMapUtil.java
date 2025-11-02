package com.guanyu.haigui.utils;

import cn.hutool.core.bean.BeanUtil;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
@Slf4j
@Service
@AllArgsConstructor
public class SessionMapUtil {

    private final ConcurrentHashMap<String, CustomUserDetails> sessionUserMap;

    public Long getUserIdBySessionId(String sessionId) {
        if (sessionId == null) {
            throw new RuntimeException("会话ID不能为空");
        }
        CustomUserDetails userDetails = sessionUserMap.get(sessionId);
        if (userDetails == null) {
            throw new RuntimeException("用户未登录");
        }
        return userDetails.getUserId();
    }

    public UserInfo getCurrentUser(String sessionId) {
        log.info("获取用户ID：{}", sessionId);
        if (sessionId == null) {
            throw new RuntimeException("会话ID不能为空");
        }
        CustomUserDetails userDetails = sessionUserMap.get(sessionId);
        if (userDetails == null) {
            throw new RuntimeException("用户未登录");
        }
        UserInfo userInfo = new UserInfo();
        BeanUtil.copyProperties(userDetails, userInfo);
        return userInfo;
    }

    public CustomUserDetails getCurrentUserFromSessionId(String sessionId) {
        if (sessionId == null) {
            throw new RuntimeException("会话ID不能为空");
        }
        CustomUserDetails userDetails = sessionUserMap.get(sessionId);
        if (userDetails == null) {
            throw new RuntimeException("用户未登录");
        }
        return userDetails;
    }

}
