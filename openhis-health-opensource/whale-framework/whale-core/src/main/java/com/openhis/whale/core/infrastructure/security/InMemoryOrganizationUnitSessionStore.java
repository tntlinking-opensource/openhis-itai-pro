/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 JVM 内存的科室会话存储（仅 {@code app.session-store.type=memory} 时启用）。
 */
@Component
@ConditionalOnProperty(prefix = "app.session-store", name = "type", havingValue = "memory")
public class InMemoryOrganizationUnitSessionStore implements OrganizationUnitSessionStore {

    private final ConcurrentHashMap<String, Long> store = new ConcurrentHashMap<>();

    @Override
    public Long get(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return null;
        }
        return store.get(buildSessionKey(userId, tenantId));
    }

    @Override
    public void put(Long userId, Long tenantId, Long organizationUnitId) {
        if (userId == null || tenantId == null || organizationUnitId == null) {
            return;
        }
        store.put(buildSessionKey(userId, tenantId), organizationUnitId);
    }

    @Override
    public void remove(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }
        store.remove(buildSessionKey(userId, tenantId));
    }

    private static String buildSessionKey(Long userId, Long tenantId) {
        return userId + "_" + tenantId;
    }
}
