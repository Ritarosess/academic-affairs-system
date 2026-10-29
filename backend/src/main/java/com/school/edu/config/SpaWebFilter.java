package com.school.edu.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 单页应用（SPA）前端路由回退：
 * 前端打包产物位于 classpath:/static，刷新 /student/courses 这类前端路由时
 * 服务端并不存在对应资源，需要回退到 index.html 交由 Vue Router 处理。
 * 仅在 static/index.html 存在（即已打包前端）时启用，开发期由 Vite 承担。
 */
@Slf4j
@Component
public class SpaWebFilter extends OncePerRequestFilter {

    private final boolean enabled;

    public SpaWebFilter() {
        this.enabled = new ClassPathResource("static/index.html").exists();
        log.info("SPA 路由回退过滤器: {}", enabled ? "已启用" : "未启用（未检测到 static/index.html）");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || !"GET".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        boolean isApi = path.startsWith("/api") || path.startsWith("/error");
        boolean hasExtension = path.contains(".");
        if (!isApi && !hasExtension) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
