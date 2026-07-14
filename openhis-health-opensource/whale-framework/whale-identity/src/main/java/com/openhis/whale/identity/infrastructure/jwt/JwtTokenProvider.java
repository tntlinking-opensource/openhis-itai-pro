/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JWT Token提供者
 */
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${app.jwt.impersonation-access-token-expiration:1800000}")
    private long impersonationAccessTokenExpiration;

    /**
     * 生成Access Token
     */
    public String generateAccessToken(Long userId, String username) {
        return generateAccessToken(userId, username, "PASSWORD");
    }

    /**
     * 生成Access Token（指定登录方式）
     */
    public String generateAccessToken(Long userId, String username, String loginMethod) {
        return generateAccessToken(userId, username, loginMethod, null);
    }

    /**
     * 生成Access Token（指定登录方式和医务人员ID）
     */
    public String generateAccessToken(Long userId, String username, String loginMethod, Long practitionerId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("loginMethod", loginMethod);
        if (practitionerId != null) {
            claims.put("practitionerId", practitionerId);
        }
        return generateToken(claims, accessTokenExpiration);
    }

    /**
     * 生成代理登录 Access Token（较短 TTL，Claims 含代理者信息）。
     */
    public String generateImpersonationAccessToken(
            Long userId,
            String username,
            Long practitionerId,
            ImpersonationInfo impersonation) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("loginMethod", "IMPERSONATE");
        claims.put("isImpersonated", true);
        if (practitionerId != null) {
            claims.put("practitionerId", practitionerId);
        }
        if (impersonation != null) {
            claims.put("impersonatorUserId", impersonation.impersonatorUserId());
            claims.put("impersonatorUserName", impersonation.impersonatorUserName());
            if (impersonation.impersonatorTenantId() != null) {
                claims.put("impersonatorTenantId", impersonation.impersonatorTenantId());
            }
        }
        return generateToken(claims, impersonationAccessTokenExpiration);
    }

    /**
     * 生成Refresh Token
     */
    public String generateRefreshToken(Long userId, String username) {
        return generateRefreshToken(userId, username, null);
    }

    /**
     * 生成 Refresh Token；代理会话时在 Claims 中保留代理者信息，便于 refresh 后仍维持代理态。
     */
    public String generateRefreshToken(Long userId, String username, ImpersonationInfo impersonation) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("type", "refresh");
        appendImpersonationClaims(claims, impersonation);
        return generateToken(claims, refreshTokenExpiration);
    }

    /**
     * 从Token中获取登录方式
     */
    public String getLoginMethodFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("loginMethod", String.class);
    }

    /**
     * 代理登录 Access Token 过期时间（秒）。
     */
    public long getImpersonationAccessTokenExpirationSeconds() {
        return impersonationAccessTokenExpiration / 1000;
    }

    /**
     * 从 Token 解析代理上下文；非代理 Token 返回 empty。
     */
    public Optional<ImpersonationInfo> getImpersonationInfoFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            Object impersonatorUserId = claims.get("impersonatorUserId");
            if (!(impersonatorUserId instanceof Number)) {
                return Optional.empty();
            }
            Long tenantId = null;
            Object impersonatorTenantId = claims.get("impersonatorTenantId");
            if (impersonatorTenantId instanceof Number) {
                tenantId = ((Number) impersonatorTenantId).longValue();
            }
            return Optional.of(new ImpersonationInfo(
                    ((Number) impersonatorUserId).longValue(),
                    claims.get("impersonatorUserName", String.class),
                    tenantId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 从Token中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Object userId = claims.get("userId");
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        return null;
    }

    /**
     * 从Token中获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 从Token中获取医务人员ID
     */
    public Long getPractitionerIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Object practitionerId = claims.get("practitionerId");
        if (practitionerId instanceof Number) {
            return ((Number) practitionerId).longValue();
        }
        return null;
    }

    /**
     * 验证Token
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 Token 中获取签发时间（登录时间）
     */
    public LocalDateTime getIssuedAtFromToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = getClaimsFromToken(token);
            Date issuedAt = claims.getIssuedAt();
            if (issuedAt == null) {
                return null;
            }
            return LocalDateTime.ofInstant(issuedAt.toInstant(), ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 生成Token
     */
    private String generateToken(Map<String, Object> claims, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    private void appendImpersonationClaims(Map<String, Object> claims, ImpersonationInfo impersonation) {
        if (impersonation == null || impersonation.impersonatorUserId() == null) {
            return;
        }
        claims.put("isImpersonated", true);
        claims.put("impersonatorUserId", impersonation.impersonatorUserId());
        claims.put("impersonatorUserName", impersonation.impersonatorUserName());
        if (impersonation.impersonatorTenantId() != null) {
            claims.put("impersonatorTenantId", impersonation.impersonatorTenantId());
        }
    }

    /**
     * 从Token中获取Claims
     */
    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
