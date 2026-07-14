/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.jwt;

import com.openhis.whale.core.infrastructure.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * JWT认证过滤器
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtTokenProvider tokenProvider;

    /**
     * 跳过不需要认证的路径
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        boolean shouldSkip = isAlwaysAllowed(request);

        // 调试日志
        if (logger.isDebugEnabled()) {
            logger.debug("JWT Filter - Path: {}, ShouldSkip: {}", request.getRequestURI(), shouldSkip);
        }

        return shouldSkip;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token)) {
            try {
                if (tokenProvider.validateToken(token)) {
                    Long userId = tokenProvider.getUserIdFromToken(token);
                    String username = tokenProvider.getUsernameFromToken(token);
                    Long practitionerId = tokenProvider.getPractitionerIdFromToken(token);
                    Optional<ImpersonationInfo> impersonation = tokenProvider.getImpersonationInfoFromToken(token);

                    if (userId != null && username != null) {
                        UserPrincipal userPrincipal = new UserPrincipal(
                                userId,
                                username,
                                "",
                                true,
                                null,
                                practitionerId,
                                impersonation.map(ImpersonationInfo::impersonatorUserId).orElse(null),
                                impersonation.map(ImpersonationInfo::impersonatorUserName).orElse(null),
                                impersonation.map(ImpersonationInfo::impersonatorTenantId).orElse(null));
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        if (logger.isDebugEnabled()) {
                            logger.debug("JWT认证成功: userId={}, username={}, impersonating={}, path={}",
                                    userId, username, impersonation.isPresent(), request.getRequestURI());
                        }
                    } else {
                        logger.warn("JWT token解析失败: userId或username为空, path={}", request.getRequestURI());
                    }
                } else {
                    logger.warn("JWT token验证失败: token无效或已过期, path={}", request.getRequestURI());
                }
            } catch (Exception e) {
                logger.error("JWT token处理异常: path={}, error={}", request.getRequestURI(), e.getMessage(), e);
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("请求中未找到JWT token: path={}", request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isAlwaysAllowed(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = normalizePath(request);
        if (path == null) {
            return false;
        }
        if (isAuthPath(path)) {
            return true;
        }
        return path.equals("/api/health")
                || path.equals("/actuator/health")
                || path.equals("/actuator/health/");
    }

    private static boolean isAuthPath(String path) {
        return path.equals("/api/app/auth/login")
                || path.equals("/api/app/auth/refreshToken")
                || path.equals("/api/app/auth/logout")
                || path.equals("/api/app/auth/stopImpersonating")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/logout");
    }

    private static String normalizePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return null;
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
            if (path.isEmpty()) {
                return "/";
            }
        }
        return path;
    }

    /**
     * 从请求中获取Token
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

}
