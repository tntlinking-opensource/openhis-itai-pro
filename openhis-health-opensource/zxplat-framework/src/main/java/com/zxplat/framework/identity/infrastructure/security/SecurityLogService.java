/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.infrastructure.security;

import com.zxplat.framework.core.application.util.JsonUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import com.zxplat.framework.identity.domain.SecurityLog;
import com.zxplat.framework.identity.infrastructure.repository.SecurityLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 安全日志服务
 */
@Service
public class SecurityLogService {

    @Autowired
    private SecurityLogRepository securityLogRepository;

    @Autowired
    private ICurrentUser currentUser;

    /**
     * 记录安全日志
     */
    public void log(String action, String result, String failureReason) {
        SecurityLog log = new SecurityLog();
        log.setAction(action);
        log.setResult(result);
        log.setFailureReason(failureReason);
        log.setCreatedAt(LocalDateTime.now());

        // 设置用户信息
        if (currentUser.isAuthenticated()) {
            log.setUserId(currentUser.getCurrentUserId());
            log.setUserName(currentUser.getCurrentUserName());
        }

        // 设置租户信息
        log.setTenantId(TenantUtils.getCurrentTenantId());

        // 获取请求信息
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                log.setClientIp(getClientIp(request));
                log.setUserAgent(request.getHeader("User-Agent"));
            }
        } catch (Exception e) {
            // 忽略获取请求信息的异常
        }

        securityLogRepository.save(log);
    }

    /**
     * 记录登录成功
     */
    public void logLoginSuccess(Long userId, String userName) {
        log("LOGIN", "SUCCESS", null);
    }

    /**
     * 记录登录失败
     */
    public void logLoginFailure(String userName, String reason) {
        SecurityLog log = new SecurityLog();
        log.setAction("LOGIN");
        log.setResult("FAILED");
        log.setFailureReason(reason);
        log.setUserName(userName);
        log.setCreatedAt(LocalDateTime.now());
        log.setTenantId(TenantUtils.getCurrentTenantId());

        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                log.setClientIp(getClientIp(request));
                log.setUserAgent(request.getHeader("User-Agent"));
            }
        } catch (Exception e) {
            // 忽略
        }

        securityLogRepository.save(log);
    }

    /**
     * 记录登出
     */
    public void logLogout() {
        log("LOGOUT", "SUCCESS", null);
    }

    /**
     * 记录代理登录开始（审计主体为代理者，目标用户写入 extraProperties）。
     *
     * @param impersonatorId 代理者用户 ID
     * @param impersonatorUserName 代理者用户名
     * @param targetUserId 被代理用户 ID
     * @param targetUserName 被代理用户名
     * @param reason 可选审计原因
     */
    public void logImpersonationStarted(
            Long impersonatorId,
            String impersonatorUserName,
            Long targetUserId,
            String targetUserName,
            String reason) {
        SecurityLog securityLog = new SecurityLog();
        securityLog.setAction("IMPERSONATION_START");
        securityLog.setResult("SUCCESS");
        securityLog.setUserId(impersonatorId);
        securityLog.setUserName(impersonatorUserName);
        securityLog.setCreatedAt(LocalDateTime.now());
        securityLog.setTenantId(TenantUtils.getCurrentTenantId());
        securityLog.setExtraProperties(buildImpersonationExtraProperties(targetUserId, targetUserName, reason));
        fillRequestInfo(securityLog);
        securityLogRepository.save(securityLog);
    }

    /**
     * 记录代理登录结束（审计主体仍为代理者）。
     *
     * @param impersonatorId 代理者用户 ID
     * @param impersonatorUserName 代理者用户名
     * @param targetUserId 被代理用户 ID
     * @param targetUserName 被代理用户名
     */
    public void logImpersonationEnded(
            Long impersonatorId,
            String impersonatorUserName,
            Long targetUserId,
            String targetUserName) {
        SecurityLog securityLog = new SecurityLog();
        securityLog.setAction("IMPERSONATION_END");
        securityLog.setResult("SUCCESS");
        securityLog.setUserId(impersonatorId);
        securityLog.setUserName(impersonatorUserName);
        securityLog.setCreatedAt(LocalDateTime.now());
        securityLog.setTenantId(TenantUtils.getCurrentTenantId());
        securityLog.setExtraProperties(buildImpersonationExtraProperties(targetUserId, targetUserName, null));
        fillRequestInfo(securityLog);
        securityLogRepository.save(securityLog);
    }

    /**
     * 记录密码修改
     */
    public void logPasswordChange() {
        log("PASSWORD_CHANGE", "SUCCESS", null);
    }

    private String buildImpersonationExtraProperties(Long targetUserId, String targetUserName, String reason) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("targetUserId", targetUserId);
        extra.put("targetUserName", targetUserName);
        if (reason != null && !reason.isBlank()) {
            extra.put("reason", reason.trim());
        }
        return JsonUtils.toJson(extra);
    }

    private void fillRequestInfo(SecurityLog log) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                log.setClientIp(getClientIp(request));
                log.setUserAgent(request.getHeader("User-Agent"));
            }
        } catch (Exception e) {
            // 忽略
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
