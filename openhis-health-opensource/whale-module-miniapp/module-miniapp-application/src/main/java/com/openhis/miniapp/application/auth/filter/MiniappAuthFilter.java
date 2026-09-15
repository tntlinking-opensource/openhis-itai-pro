/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.auth.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.miniapp.application.config.MiniappProperties;
import com.openhis.miniapp.domain.auth.service.MapAuthDomainService;
import com.openhis.miniapp.domain.auth.service.MapAuthDomainService.ResolvedSession;
import com.openhis.miniapp.domain.context.MiniappContext;
import com.openhis.whale.core.application.dto.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 小程序鉴权过滤器：解析 Bearer / X-Miniapp-Token，注入 {@link MiniappContext}（C2）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
@RequiredArgsConstructor
public class MiniappAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_TOKEN = "X-Miniapp-Token";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    private static final Set<String> ANONYMOUS_SUFFIXES = Set.of(
        "/miniappAuth/autoLogin",
        "/miniappAuth/devLogin");

    private final MapAuthDomainService mapAuthDomainService;
    private final MiniappProperties miniappProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.contains("/api/miniapp/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (!assertGatewayKey(request, response)) {
                return;
            }
            String path = request.getRequestURI();
            if (isAnonymous(path)) {
                filterChain.doFilter(request, response);
                return;
            }
            String rawToken = resolveToken(request);
            if (rawToken == null || rawToken.isBlank()) {
                writeUnauthorized(response, "未登录：缺少小程序会话令牌");
                return;
            }
            Optional<ResolvedSession> resolved = mapAuthDomainService.resolveActiveSession(rawToken);
            if (resolved.isEmpty()) {
                writeUnauthorized(response, "会话无效或已过期，请重新登录");
                return;
            }
            ResolvedSession hit = resolved.get();
            MiniappContext.set(MiniappContext.builder()
                .accountId(hit.getAccount().getId())
                .openid(hit.getAccount().getOpenid())
                .sessionId(String.valueOf(hit.getSession().getId()))
                .patientId(hit.getSession().getCurrentPatientId())
                .practitionerId(hit.getSession().getCurrentPractitionerId())
                .tenantId(hit.getSession().getTenantId())
                .appId(hit.getAccount().getAppId())
                .build());
            filterChain.doFilter(request, response);
        } finally {
            MiniappContext.clear();
        }
    }

    private static boolean isAnonymous(String path) {
        for (String suffix : ANONYMOUS_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveToken(HttpServletRequest request) {
        String headerToken = request.getHeader(HEADER_TOKEN);
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken.trim();
        }
        String authorization = request.getHeader(HEADER_AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return null;
    }

    private boolean assertGatewayKey(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String configured = miniappProperties.getGatewayApiKey();
        if (configured == null || configured.isBlank()) {
            return true;
        }
        String incoming = request.getHeader(MiniappProperties.GATEWAY_API_KEY_HEADER);
        if (configured.equals(incoming)) {
            return true;
        }
        writeError(response, HttpServletResponse.SC_FORBIDDEN, "GATEWAY_FORBIDDEN", "请经小程序网关访问");
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    private void writeError(
            HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.error(code, message));
    }
}
