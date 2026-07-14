/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单的限流拦截器（基于内存）
 * 注意：生产环境建议使用Redis等分布式限流方案
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final ConcurrentHashMap<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientId = getClientId(request);
        long currentTime = System.currentTimeMillis();

        RateLimitInfo info = rateLimitMap.computeIfAbsent(clientId, k -> new RateLimitInfo());

        // 如果超过1分钟，重置计数
        if (currentTime - info.getLastResetTime() > 60000) {
            info.reset(currentTime);
        }

        // 检查是否超过限制
        if (info.getCount().get() >= MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(429); // HTTP 429 Too Many Requests
            response.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_MINUTE));
            response.setHeader("X-RateLimit-Remaining", "0");
            return false;
        }

        // 增加计数
        info.getCount().incrementAndGet();
        response.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_MINUTE));
        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(MAX_REQUESTS_PER_MINUTE - info.getCount().get()));

        return true;
    }

    private String getClientId(HttpServletRequest request) {
        // 优先使用IP地址作为客户端标识
        String ip = request.getRemoteAddr();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            ip = forwardedFor.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 限流信息
     */
    private static class RateLimitInfo {
        private final AtomicInteger count = new AtomicInteger(0);
        private long lastResetTime = System.currentTimeMillis();

        public AtomicInteger getCount() {
            return count;
        }

        public long getLastResetTime() {
            return lastResetTime;
        }

        public void reset(long currentTime) {
            count.set(0);
            lastResetTime = currentTime;
        }
    }
}
