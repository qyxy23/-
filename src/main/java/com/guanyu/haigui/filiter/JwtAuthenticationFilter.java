package com.guanyu.haigui.filiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.Exception.TokenErrorException;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.utils.JwtTokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final ObjectMapper objectMapper; // 注入JSON序列化工具
    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain
    ) throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        // 跳过不需要认证的路径（需与SecurityConfig一致）
        List<String> excludePaths = List.of(
                "/ws/**",
                "/user/login",
                "/user/register",
                "/api/haigui/ranking/soup-list",
                "/api/haigui/ranking/soup/**",
                "/searchLobbies");
        boolean shouldSkip = excludePaths.stream()
                .anyMatch(excludePath -> pathMatcher.match(excludePath, requestURI));

        if (shouldSkip) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            try {
                // 1. 验证Token有效性（签名+过期）—— 此处会抛TokenErrorException
                if (!jwtTokenUtil.validateToken(token)) {
                    log.warn("无效或过期的Token: {}", token);
                    sendUnauthorizedResponse(response, "Token认证已过期");
                    return;
                }

                // 2. 解析Token中的身份信息（此时Token有效，不会抛TokenErrorException）
                String username = jwtTokenUtil.getUsernameFromToken(token);
                List<String> roles = jwtTokenUtil.getRolesFromToken(token);
                log.debug("用户{}的Token验证通过，角色：{}", username, roles);

                // 3. 构造Spring Security的Authentication对象（携带角色权限）
                List<GrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        authorities
                );

                // 4. 将Authentication注入SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("用户{}的Token已注入SecurityContext", username);

            } catch (TokenErrorException e) {
                // 捕获Token验证异常（如签名错误、过期）
                log.error("Token验证失败: {}", e.getMessage());
                sendUnauthorizedResponse(response, e.getMessage());
                return;
            } catch (Exception e) {
                // 捕获其他异常（如解析失败、IO异常）
                log.error("解析Token身份失败: {}", e.getMessage());
                SecurityContextHolder.clearContext(); // 清除脏数据
                sendUnauthorizedResponse(response, "Token解析失败");
                return;
            }
        }

        // Token有效或无Token时，继续执行后续过滤器链
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头提取Bearer Token
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        return StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")
                ? bearerToken.substring(7).trim()
                : null;
    }

    /**
     * 发送401 Unauthorized响应（统一错误格式）
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401状态码
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); // JSON格式
        response.setCharacterEncoding("UTF-8");
        // 写入自定义错误响应
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(401, message)));
    }
}