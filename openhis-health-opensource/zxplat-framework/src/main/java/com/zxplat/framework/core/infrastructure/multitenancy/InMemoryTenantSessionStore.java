/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.multitenancy;

import com.zxplat.framework.core.infrastructure.security.LoginSessionStoreKeys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 JVM 内存的租户会话存储（仅 {@code app.session-store.type=memory} 时启用）。
 *
 * <p>键为登录会话实例，而非 userId。</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.session-store", name = "type", havingValue = "memory")
public class InMemoryTenantSessionStore implements TenantSessionStore {

    private final ConcurrentHashMap<String, Long> store = new ConcurrentHashMap<>();

    @Override
    public Long get(Long userId) {
        String key = LoginSessionStoreKeys.resolve(userId);
        if (key == null) {
            return null;
        }
        return store.get(key);
    }

    @Override
    public void put(Long userId, Long tenantId) {
        String key = LoginSessionStoreKeys.resolve(userId);
        if (key == null || tenantId == null) {
            return;
        }
        store.put(key, tenantId);
    }

    @Override
    public void remove(Long userId) {
        String key = LoginSessionStoreKeys.resolve(userId);
        if (key == null) {
            return;
        }
        store.remove(key);
    }
}
