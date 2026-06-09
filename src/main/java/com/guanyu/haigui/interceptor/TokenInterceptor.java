package com.guanyu.haigui.interceptor;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.utils.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.Resource;
import java.util.List;

@Component
@Slf4j
public class TokenInterceptor implements HandlerInterceptor {
    @Resource
    private JwtTokenUtil jwtTokenUtil;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 允许匿名访问；若携带有效 Token 仍会解析并写入 BaseContext */
    private static final List<String> OPTIONAL_AUTH_PATHS = List.of(
            "/api/haigui/ranking/soup-list",
            "/api/haigui/ranking/soup/**",
            "/searchLobbies");

    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String requestUri = request.getRequestURI();
        boolean optionalAuth = OPTIONAL_AUTH_PATHS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, requestUri));

        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token)) {
            if (optionalAuth) {
                return true;
            }
            log.warn("Missing token in request header");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        if (!token.startsWith("Bearer ")) {
            log.warn("Invalid token format: {}", token);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        token = token.substring(7);
        try {
            jwtTokenUtil.validateToken(token);
            Long empId = jwtTokenUtil.getUserIdFromToken(token);
            log.info("当前用户id：{}", empId);
            BaseContext.setCurrentId(empId);
            return true;
        } catch (Exception ex) {
            if (optionalAuth) {
                log.debug("Optional auth path with invalid token, treated as guest: {}", requestUri);
                return true;
            }
            log.error("Token validation failed", ex);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                                @NotNull Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }
}
