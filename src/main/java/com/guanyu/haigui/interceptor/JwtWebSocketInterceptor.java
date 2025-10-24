package com.guanyu.haigui.interceptor;

import com.guanyu.haigui.config.WebSocketConfig;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import com.guanyu.haigui.utils.JwtTokenUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtWebSocketInterceptor implements HandshakeInterceptor {

    @Value("${spring.profiles.active}")
    private String active;

    @Resource
    private JwtTokenUtil jwtTokenUtil;
    // private final ConcurrentHashMap<String, UserInfo> sessionUserMap;

    @Autowired
    private ApplicationContext applicationContext;

    // 使用时获取Bean
    private WebSocketConfig getWebSocketConfig() {
        return applicationContext.getBean(WebSocketConfig.class);
    }

    /**
     * 握手前：验证JWT有效性，将用户信息存入WebSocket Session
     */
    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response,
            @NotNull WebSocketHandler wsHandler, @NotNull Map<String, Object> attributes) {
        // if (active.equals("dev")){
        // log.info("当前环境为开发环境，跳过JWT认证");
        // return true;
        // }
        try {
            System.out.println("当前环境: " + active);
            log.info("===== WebSocket握手开始 =====");

            // 1. Extract and validate Token
            String token = extractToken(request);

            boolean tokenValid = jwtTokenUtil.validateToken(token);
            log.info("Token验证结果: {}", tokenValid);

            if (!tokenValid) {
                log.warn("无效的token，握手失败");
                return false;
            }

            // 2. Extract user information
            String username = jwtTokenUtil.getUsernameFromToken(token);
            Long userId = jwtTokenUtil.getUserIdFromToken(token);
            List<String> roleNames = jwtTokenUtil.getRolesFromToken(token);
            // 3. 将角色转换为GrantedAuthority（Spring Security要求的权限格式，如"ROLE_ADMIN"）
            List<GrantedAuthority> authorities = roleNames.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role)) // 给角色加"ROLE_"前缀（Spring Security默认要求）
                    .collect(Collectors.toList());
            log.info("用户ID: {}, 用户名: {}", userId, username);

            CustomUserDetails userPrincipal = new CustomUserDetails(userId, username, authorities);

            // 3. Store user info into attributes (will be passed to WebSocket session)
            // 关键：设置Principal，这是Spring Security检测的关键
            attributes.put(Principal.class.getName(), userPrincipal);
            log.info("会话attributes里的用户信息: {}", attributes);

            return true;
        } catch (Exception e) {
            log.error("握手失败ERROR!", e);
            return false;
        } finally {
            log.info("===== WebSocket握手成功=====");
        }
    }

    // Extract Token method (clear logic)
    private String extractToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest servletReq = servletRequest.getServletRequest();

            // Extract from URL parameters
            String query = servletReq.getQueryString();
            log.info("URL query params: {}", query);

            if (StringUtils.hasText(query)) {
                UriComponents uriComponents = UriComponentsBuilder.fromUriString("?" + query).build();
                String tokenFromQuery = uriComponents.getQueryParams().getFirst("token");
                if (StringUtils.hasText(tokenFromQuery)) {
                    log.info("Token extracted from URL parameters");
                    return tokenFromQuery.startsWith("Bearer ") ? tokenFromQuery.substring(7) : tokenFromQuery;
                }
            }

            // Extract from Header
            String authHeader = servletReq.getHeader("Authorization");
            log.info("Authorization header: {}", authHeader);

            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                log.info("Token extracted from header");
                return authHeader.substring(7).trim();
            }
        }
        log.info("No token extracted");
        return null;
    }

    @Override
    public void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response,
            @NotNull WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后无需额外操作
        // if (exception != null) {
        // log.info("WebSocket 握手出现异常：{}", exception.getMessage());
        // }
    }
}