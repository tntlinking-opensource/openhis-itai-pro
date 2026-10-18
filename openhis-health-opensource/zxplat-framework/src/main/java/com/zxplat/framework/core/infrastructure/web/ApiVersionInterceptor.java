/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API版本拦截器
 * 从Header中读取API版本信息（可选功能）
 */
@Component
public class ApiVersionInterceptor implements HandlerInterceptor {

    private static final String API_VERSION_HEADER = "X-API-Version";
    private static final String API_VERSION_ATTRIBUTE = "apiVersion";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String apiVersion = request.getHeader(API_VERSION_HEADER);
        if (apiVersion != null && !apiVersion.trim().isEmpty()) {
            request.setAttribute(API_VERSION_ATTRIBUTE, apiVersion.trim());
        }
        return true;
    }
}
