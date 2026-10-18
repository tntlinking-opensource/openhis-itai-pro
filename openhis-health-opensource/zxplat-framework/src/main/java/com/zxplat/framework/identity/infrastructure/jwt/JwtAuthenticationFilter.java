/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.infrastructure.jwt;

import com.zxplat.framework.core.infrastructure.security.LoginSessionIdRequestContext;
import com.zxplat.framework.core.infrastructure.security.LoginSessionStoreKeys;
import com.zxplat.framework.core.infrastructure.security.UserPrincipal;
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

        try {
            String token = getTokenFromRequest(request);

            if (StringUtils.hasText(token)) {
                try {
                    if (tokenProvider.validateToken(token)) {
                        Long userId = tokenProvider.getUserIdFromToken(token);
                        String username = tokenProvider.getUsernameFromToken(token);
                        Long practitionerId = tokenProvider.getPractitionerIdFromToken(token);
                        Optional<ImpersonationInfo> impersonation = tokenProvider.getImpersonationInfoFromToken(token);
                        String loginSessionId = resolveLoginSessionId(token, userId);

                        if (userId != null && username != null) {
                            LoginSessionIdRequestContext.set(loginSessionId);
                            Long impersonatorUserId = null;
                            String impersonatorUserName = null;
                            Long impersonatorTenantId = null;
                            // 代理 Token：把代理者写入 Principal，供审计与 stopImpersonating
                            if (impersonation.isPresent()) {
                                ImpersonationInfo info = impersonation.get();
                                impersonatorUserId = info.impersonatorUserId();
                                impersonatorUserName = info.impersonatorUserName();
                                impersonatorTenantId = info.impersonatorTenantId();
                            }
                            UserPrincipal userPrincipal = new UserPrincipal(
                                    userId,
                                    username,
                                    "",
                                    true,
                                    null,
                                    practitionerId,
                                    impersonatorUserId,
                                    impersonatorUserName,
                                    impersonatorTenantId,
                                    loginSessionId);
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            userPrincipal, null, userPrincipal.getAuthorities());
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
        } finally {
            // 线程池复用时不得把上一请求的会话实例带到下一请求
            LoginSessionIdRequestContext.clear();
        }
    }

    /**
     * CORS 预检、登录/refresh、健康检查不解析 JWT。
     *
     * @param request 当前请求
     * @return true 则跳过过滤器
     */
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

    /**
     * 登录与 refresh 不走本过滤器（尚无/使用 Refresh Token）；登出与结束代理须解析 Access Token 以定位会话实例。
     *
     * @param path 规范化 URI
     * @return true 则跳过 JWT 解析
     */
    private static boolean isAuthPath(String path) {
        return path.equals("/api/app/auth/login")
                || path.equals("/api/app/auth/refreshToken")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/refresh");
    }

    /**
     * 解析登录会话实例：优先 JWT claim，旧 Token 回落 {@code u:{userId}}。
     *
     * @param token  Access Token
     * @param userId 用户主键
     * @return 会话存储键
     */
    private String resolveLoginSessionId(String token, Long userId) {
        String fromToken = tokenProvider.getLoginSessionIdFromToken(token);
        if (fromToken != null) {
            String trimmed = fromToken.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return LoginSessionStoreKeys.legacyKey(userId);
    }

    /**
     * 去掉 context-path，得到与 Security 白名单可比较的 URI。
     *
     * @param request 当前请求
     * @return 规范化路径；URI 空则 {@code null}
     */
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
     * 从 Authorization Bearer 头取出 Access Token。
     *
     * @param request 当前请求
     * @return Token 原文；无头则 {@code null}
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

}
