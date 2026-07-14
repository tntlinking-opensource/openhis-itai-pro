/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.multitenancy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 JVM 内存的租户会话存储（仅 {@code app.session-store.type=memory} 时启用）。
 */
@Component
@ConditionalOnProperty(prefix = "app.session-store", name = "type", havingValue = "memory")
public class InMemoryTenantSessionStore implements TenantSessionStore {

    private final ConcurrentHashMap<Long, Long> store = new ConcurrentHashMap<>();

    @Override
    public Long get(Long userId) {
        if (userId == null) {
            return null;
        }
        return store.get(userId);
    }

    @Override
    public void put(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }
        store.put(userId, tenantId);
    }

    @Override
    public void remove(Long userId) {
        if (userId == null) {
            return;
        }
        store.remove(userId);
    }
}
