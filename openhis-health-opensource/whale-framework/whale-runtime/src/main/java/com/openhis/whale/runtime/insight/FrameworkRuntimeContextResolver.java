/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.runtime.insight;

import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.core.infrastructure.version.ProductVersionFiles;
import com.openhis.whale.identity.infrastructure.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 框架运行时洞察公共上下文（版本、登录用户、登录时间）。
 */
@Component
public class FrameworkRuntimeContextResolver {

    private final ICurrentUser currentUser;
    private final JwtTokenProvider jwtTokenProvider;

    public FrameworkRuntimeContextResolver(ICurrentUser currentUser, JwtTokenProvider jwtTokenProvider) {
        this.currentUser = currentUser;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public Map<String, Object> buildCommonContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(
                "softwareVersion",
                ProductVersionFiles.readDisplayVersion(FrameworkRuntimeContextResolver.class).orElse(""));
        context.put("loginUserId", resolveLoginUserId());
        context.put("loginTime", resolveLoginTime());
        return context;
    }

    public Map<String, Object> buildOrganizationPayload(String organizationName, String organizationTypeCode) {
        Map<String, Object> context = buildCommonContext();
        context.put("organizationType", organizationTypeCode);
        context.put("organizationName", organizationName);
        return context;
    }

    public Long resolveLoginUserId() {
        if (currentUser != null && currentUser.isAuthenticated()) {
            return currentUser.getCurrentUserId();
        }
        return null;
    }

    private LocalDateTime resolveLoginTime() {
        String token = resolveBearerToken();
        if (token == null) {
            return null;
        }
        return jwtTokenProvider.getIssuedAtFromToken(token);
    }

    private static String resolveBearerToken() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                return header.substring(7);
            }
        } catch (Exception ignored) {
            // 无 HTTP 上下文时忽略
        }
        return null;
    }
}
