/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 JVM 内存的科室会话存储（仅 {@code app.session-store.type=memory} 时启用）。
 *
 * <p>键为登录会话实例（JWT {@code loginSessionId}），禁止按 userId 让多浏览器互相覆盖科室。</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.session-store", name = "type", havingValue = "memory")
public class InMemoryOrganizationUnitSessionStore implements OrganizationUnitSessionStore {

    private final ConcurrentHashMap<String, Long> store = new ConcurrentHashMap<>();

    @Override
    public Long get(Long userId, Long tenantId) {
        String key = buildKey(userId, tenantId);
        if (key == null) {
            return null;
        }
        return store.get(key);
    }

    @Override
    public void put(Long userId, Long tenantId, Long organizationUnitId) {
        String key = buildKey(userId, tenantId);
        if (key == null || organizationUnitId == null) {
            return;
        }
        store.put(key, organizationUnitId);
    }

    @Override
    public void remove(Long userId, Long tenantId) {
        String key = buildKey(userId, tenantId);
        if (key == null) {
            return;
        }
        store.remove(key);
    }

    /**
     * 会话实例 + 租户：换租户后旧科室键自然失效。
     *
     * @param userId   用户主键
     * @param tenantId 租户主键
     * @return 存储键；缺会话或租户则空
     */
    private static String buildKey(Long userId, Long tenantId) {
        String sessionId = LoginSessionStoreKeys.resolve(userId);
        if (sessionId == null || tenantId == null) {
            return null;
        }
        return sessionId + "_" + tenantId;
    }
}
