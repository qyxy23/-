package com.guanyu.haigui.filiter;

import com.github.pagehelper.PageHelper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 防止 PageHelper 线程变量泄漏，污染后续请求的 MyBatis 查询。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PageHelperCleanupFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            PageHelper.clearPage();
        }
    }
}
