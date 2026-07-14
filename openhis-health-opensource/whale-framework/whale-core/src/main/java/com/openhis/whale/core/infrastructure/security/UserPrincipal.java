/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 用户主体
 * 实现UserDetails接口，用于Spring Security认证
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

    public UserPrincipal(Long userId, String username, String password, boolean enabled) {
        this(userId, username, password, enabled, Collections.emptyList(), null, null, null, null);
    }

    public UserPrincipal(Long userId, String username, String password, boolean enabled, Long practitionerId) {
        this(userId, username, password, enabled, Collections.emptyList(), practitionerId, null, null, null);
    }

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
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities != null ? authorities : Collections.emptyList();
        this.practitionerId = practitionerId;
        this.impersonatorUserId = impersonatorUserId;
        this.impersonatorUserName = impersonatorUserName;
        this.impersonatorTenantId = impersonatorTenantId;
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
