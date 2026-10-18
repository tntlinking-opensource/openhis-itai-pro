/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.infrastructure.session;

import com.zxplat.framework.core.infrastructure.multitenancy.HospitalSessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.session-store", name = "type", havingValue = "jdbc", matchIfMissing = true)
public class JpaHospitalSessionStore implements HospitalSessionStore {

    private final UserLoginSessionSupport sessionSupport;

    public JpaHospitalSessionStore(UserLoginSessionSupport sessionSupport) {
        this.sessionSupport = sessionSupport;
    }

    @Override
    public Long get(Long userId, Long tenantId) {
        return sessionSupport.getHospitalOrganizationId(userId, tenantId);
    }

    @Override
    public void put(Long userId, Long tenantId, Long hospitalOrganizationId) {
        sessionSupport.putHospitalOrganizationId(userId, tenantId, hospitalOrganizationId);
    }

    @Override
    public void remove(Long userId, Long tenantId) {
        sessionSupport.removeHospitalOrganizationId(userId, tenantId);
    }
}
