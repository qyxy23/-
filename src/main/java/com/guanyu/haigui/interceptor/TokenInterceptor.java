package com.guanyu.haigui.interceptor;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.utils.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;

@Component
@Slf4j
public class TokenInterceptor implements HandlerInterceptor {
    @Resource
    private JwtTokenUtil jwtTokenUtil;


    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }
        System.out.println("当前请求的资源：" + handler);

        //1、从请求头中获取令牌
        String token = request.getHeader( "Authorization" );
        log.info("当前请求的令牌：{}", token);
        // 检查token是否为null
        if (token == null) {
            log.warn("Missing token in request header");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 检查token是否以"Bearer "开头
        if (!token.startsWith("Bearer ")) {
            log.warn("Invalid token format: {}", token);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        //去掉Bearer
        token = token.substring(7);
        //2、校验令牌
        try {
            log.info("jwt校验:{}", token);
            jwtTokenUtil.validateToken(token);
            Long empId = jwtTokenUtil.getUserIdFromToken(token);
            log.info("当前用户id：{}", empId);
            System.out.println(empId+"通过token");
            //设置当前登录用户id到当前线程中
            BaseContext.setCurrentId(empId);
            //3、通过，放行
            return true;
        } catch (Exception ex) {
            //4、不通过，响应401状态码
            log.error("Token validation failed", ex);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }
}
