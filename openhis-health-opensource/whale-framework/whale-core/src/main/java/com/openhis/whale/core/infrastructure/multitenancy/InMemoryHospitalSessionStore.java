/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.multitenancy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "app.session-store", name = "type", havingValue = "memory")
public class InMemoryHospitalSessionStore implements HospitalSessionStore {

    private final ConcurrentHashMap<String, Long> store = new ConcurrentHashMap<>();

    @Override
    public Long get(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return null;
        }
        return store.get(buildKey(userId, tenantId));
    }

    @Override
    public void put(Long userId, Long tenantId, Long hospitalOrganizationId) {
        if (userId == null || tenantId == null || hospitalOrganizationId == null) {
            return;
        }
        store.put(buildKey(userId, tenantId), hospitalOrganizationId);
    }

    @Override
    public void remove(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }
        store.remove(buildKey(userId, tenantId));
    }

    private static String buildKey(Long userId, Long tenantId) {
        return userId + "_" + tenantId;
    }
}
