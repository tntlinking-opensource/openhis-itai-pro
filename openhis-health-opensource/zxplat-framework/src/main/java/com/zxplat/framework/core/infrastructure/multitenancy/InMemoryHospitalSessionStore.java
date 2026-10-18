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
 * 基于 JVM 内存的医院会话存储（仅 {@code app.session-store.type=memory} 时启用）。
 *
 * <p>键为登录会话实例，同一用户多 Token 互不覆盖。</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.session-store", name = "type", havingValue = "memory")
public class InMemoryHospitalSessionStore implements HospitalSessionStore {

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
    public void put(Long userId, Long tenantId, Long hospitalOrganizationId) {
        String key = buildKey(userId, tenantId);
        if (key == null || hospitalOrganizationId == null) {
            return;
        }
        store.put(key, hospitalOrganizationId);
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
     * 会话实例 + 租户：换租户后旧医院键自然失效。
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
