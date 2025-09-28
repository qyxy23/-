package com.guanyu.haigui.interceptor;

import com.guanyu.haigui.pojo.model.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取用户信息
        User user = (User) request.getSession().getAttribute("user");
        if (user != null) {
            return true;
        }
        // 未登录，返回登录页面
        response.sendRedirect("/login.html");
        return false;
    }
}
