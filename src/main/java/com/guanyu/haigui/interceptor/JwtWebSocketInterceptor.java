package com.guanyu.haigui.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import com.guanyu.haigui.utils.JwtTokenUtil;
import java.util.Map;

@Slf4j
@Component
public class JwtWebSocketInterceptor implements HandshakeInterceptor {

    private final JwtTokenUtil jwtTokenUtil;

    public JwtWebSocketInterceptor(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    /**
     * 握手前：验证JWT有效性，将用户信息存入WebSocket Session
     */
    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, @NotNull Map<String, Object> attributes) throws Exception {
        // 1. 从HTTP请求头获取JWT Token（若用Query参数，需从request.getURI().getQuery()解析）
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getHeader("Authorization");
            if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
                token = token.substring(7).trim();
            }

            // 2. 验证JWT有效性
            if (jwtTokenUtil.validateToken(token)) {
                // 3. 提取用户信息（如UserID、Username），存入WebSocket Session的attributes
                String username = jwtTokenUtil.getUsernameFromToken(token);
                log.info("用户：{} 尝试连接 WebSocket", username);
                Long userId = jwtTokenUtil.getUserIdFromToken(token);
                attributes.put("username", username);
                attributes.put("userId", userId);
                return true; // 允许握手升级
            }
        }
        return false; // 拒绝握手
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后无需额外操作
    }
}