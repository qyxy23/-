package com.guanyu.haigui.interceptor;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.utils.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
@AllArgsConstructor
@Slf4j
@Component
public class JwtWebSocketInterceptor implements HandshakeInterceptor {

    private final JwtTokenUtil jwtTokenUtil;

    /**
     * 握手前：验证JWT有效性，将用户信息存入WebSocket Session
     */
    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, @NotNull Map<String, Object> attributes) throws Exception {
        // 放行SockJS的/info探测请求
        if (request.getURI().getPath().contains("/ws/info")) {
            return true;
        }

        String token = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest servletReq = servletRequest.getServletRequest();

            // 1. 从URL参数获取Token（SockJS握手请求的Token在此）
            String query = servletReq.getQueryString();
            if (StringUtils.hasText(query)) {
                UriComponents uriComponents = UriComponentsBuilder.fromUriString("?" + query).build();
                String tokenFromQuery = uriComponents.getQueryParams().getFirst("token");
                if (StringUtils.hasText(tokenFromQuery)) {
                    // 去掉Bearer前缀（如果有）
                    token = tokenFromQuery.startsWith("Bearer ") ? tokenFromQuery.substring(7) : tokenFromQuery;
                }
            }

            // 2. 若URL无Token，再从请求头获取（兼容其他场景）
            if (!StringUtils.hasText(token)) {
                String authHeader = servletReq.getHeader("Authorization");
                if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7).trim();
                }
            }
        }

        // 3. 再次确保Token无Bearer前缀（双重保险）
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        // 4. 验证Token有效性
        if (StringUtils.hasText(token) && jwtTokenUtil.validateToken(token)) {
            String username = jwtTokenUtil.getUsernameFromToken(token);
            Long userId = jwtTokenUtil.getUserIdFromToken(token);
            log.info("用户：{} 尝试连接 WebSocket", username);
            BaseContext.setCurrentId(userId);
            attributes.put("username", username);
            attributes.put("userId", userId);
            return true; // 允许握手
        }
        return false; // 拒绝握手
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后无需额外操作
        if (exception != null) {
            log.info("WebSocket 握手出现异常：{}", exception.getMessage());
        }
    }
}