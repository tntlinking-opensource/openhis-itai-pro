/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.web.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Swagger诊断拦截器
 * 用于记录访问Swagger相关路径的请求信息
 */
@Component
public class SwaggerDiagnosticInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(SwaggerDiagnosticInterceptor.class);

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String path = request.getRequestURI();

        // 只记录Swagger相关路径
        if (path.contains("swagger") || path.contains("api-docs") || path.contains("swagger-config") || path.equals("/v3/api-docs")) {
            logger.info("========== Swagger诊断: 访问路径 {} ==========", path);
            logger.info("请求方法: {}", request.getMethod());
            logger.info("Handler: {}", handler != null ? handler.getClass().getName() : "null");
        }

        return true;
    }
}
