/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security 用户主体：身份字段来自 JWT，含登录会话实例以便科室/租户按 Token 隔离。
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Long practitionerId;
    private final Long impersonatorUserId;
    private final String impersonatorUserName;
    private final Long impersonatorTenantId;
    /** JWT 登录会话实例，驱动租户/医院/科室存储键。 */
    private final String loginSessionId;

    /**
     * 最简主体（无医务人员、无代理、无会话实例）。
     *
     * @param userId   用户主键
     * @param username 用户名
     * @param password 密码占位
     * @param enabled  是否启用
     */
    public UserPrincipal(Long userId, String username, String password, boolean enabled) {
        this(userId, username, password, enabled, Collections.emptyList(), null, null, null, null, null);
    }

    /**
     * 带医务人员主键的主体（后台代行等）。
     *
     * @param userId          用户主键
     * @param username        用户名
     * @param password        密码占位
     * @param enabled         是否启用
     * @param practitionerId  医务人员主键
     */
    public UserPrincipal(Long userId, String username, String password, boolean enabled, Long practitionerId) {
        this(userId, username, password, enabled, Collections.emptyList(), practitionerId, null, null, null, null);
    }

    /**
     * 含代理上下文、无显式会话实例（兼容旧构造）。
     *
     * @param userId                 用户主键
     * @param username               用户名
     * @param password               密码占位
     * @param enabled                是否启用
     * @param authorities            授权
     * @param practitionerId         医务人员
     * @param impersonatorUserId     代理者用户
     * @param impersonatorUserName   代理者用户名
     * @param impersonatorTenantId   代理者租户
     */
    public UserPrincipal(
            Long userId,
            String username,
            String password,
            boolean enabled,
            Collection<? extends GrantedAuthority> authorities,
            Long practitionerId,
            Long impersonatorUserId,
            String impersonatorUserName,
            Long impersonatorTenantId) {
        this(
                userId,
                username,
                password,
                enabled,
                authorities,
                practitionerId,
                impersonatorUserId,
                impersonatorUserName,
                impersonatorTenantId,
                null);
    }

    /**
     * 完整构造：含登录会话实例，供 JWT 过滤器注入。
     *
     * @param userId                 用户主键
     * @param username               用户名
     * @param password               密码占位
     * @param enabled                是否启用
     * @param authorities            授权
     * @param practitionerId         医务人员
     * @param impersonatorUserId     代理者用户
     * @param impersonatorUserName   代理者用户名
     * @param impersonatorTenantId   代理者租户
     * @param loginSessionId         JWT 会话实例
     */
    public UserPrincipal(
            Long userId,
            String username,
            String password,
            boolean enabled,
            Collection<? extends GrantedAuthority> authorities,
            Long practitionerId,
            Long impersonatorUserId,
            String impersonatorUserName,
            Long impersonatorTenantId,
            String loginSessionId) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        if (authorities != null) {
            this.authorities = authorities;
        } else {
            this.authorities = Collections.emptyList();
        }
        this.practitionerId = practitionerId;
        this.impersonatorUserId = impersonatorUserId;
        this.impersonatorUserName = impersonatorUserName;
        this.impersonatorTenantId = impersonatorTenantId;
        this.loginSessionId = loginSessionId;
    }

    public boolean isImpersonating() {
        return impersonatorUserId != null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
