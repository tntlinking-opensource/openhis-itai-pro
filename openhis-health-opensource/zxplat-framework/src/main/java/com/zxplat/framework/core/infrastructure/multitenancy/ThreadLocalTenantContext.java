/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.multitenancy;

import org.springframework.stereotype.Component;

/**
 * 基于ThreadLocal的租户上下文实现
 */
@Component
public class ThreadLocalTenantContext implements TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    @Override
    public Long getCurrentTenantId() {
        return TENANT_ID.get();
    }

    @Override
    public void setCurrentTenantId(Long tenantId) {
        if (tenantId != null) {
            TENANT_ID.set(tenantId);
        } else {
            TENANT_ID.remove();
        }
    }

    @Override
    public void clear() {
        TENANT_ID.remove();
    }
}
