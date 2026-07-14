/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.session;

import com.openhis.whale.core.infrastructure.multitenancy.TenantSessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于数据库的租户会话存储（默认；重启后会话仍有效）。
 */
@Component
@ConditionalOnProperty(prefix = "app.session-store", name = "type", havingValue = "jdbc", matchIfMissing = true)
public class JpaTenantSessionStore implements TenantSessionStore {

    private final UserLoginSessionSupport sessionSupport;

    public JpaTenantSessionStore(UserLoginSessionSupport sessionSupport) {
        this.sessionSupport = sessionSupport;
    }

    @Override
    public Long get(Long userId) {
        return sessionSupport.getTenantId(userId);
    }

    @Override
    public void put(Long userId, Long tenantId) {
        sessionSupport.putTenantId(userId, tenantId);
    }

    @Override
    public void remove(Long userId) {
        sessionSupport.removeTenant(userId);
    }
}
