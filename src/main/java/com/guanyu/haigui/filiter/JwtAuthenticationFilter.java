package com.guanyu.haigui.filiter;

import com.guanyu.haigui.utils.JwtTokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor // Lombok自动生成final字段的构造器
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Resource
    private final JwtTokenUtil jwtTokenUtil;

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain
    ) throws ServletException, IOException {
        // 1. 从请求头提取Token（去除"Bearer "前缀）
        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            // 2. 验证Token有效性（签名+过期）
            if (jwtTokenUtil.validateToken(token)) {
                try {
                    // 3. 解析Token中的身份信息
                    String username = jwtTokenUtil.getUsernameFromToken(token);
                    List<String> roles = jwtTokenUtil.getRolesFromToken(token);

                    // 4. 构造Spring Security的Authentication对象（携带角色权限）
                    List<GrantedAuthority> authorities = roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role)) // 必须加ROLE_前缀！
                            .collect(Collectors.toList());

                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                            username, // 用户名（对应UserDetails的username）
                            null,     // 密码（JWT无需密码）
                            authorities // 用户权限（角色）
                    );

                    // 5. 将Authentication注入SecurityContext（后续流程可通过SecurityContextHolder获取）
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("用户{}的Token验证通过，角色：{}", username, roles);
                } catch (Exception e) {
                    log.error("解析Token身份失败: {}", e.getMessage());
                    // 可选：清除SecurityContext，避免脏数据
                    SecurityContextHolder.clearContext();
                }
            } else {
                log.warn("无效或过期的Token: {}", token);
                // 可选：返回401 Unauthorized（需结合全局异常处理）
            }
        }

        // 6. 继续执行后续过滤器链
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头提取Bearer Token
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        return StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")
                ? bearerToken.substring(7).trim() // 去除"Bearer "前缀并 trim 空格
                : null;
    }
}