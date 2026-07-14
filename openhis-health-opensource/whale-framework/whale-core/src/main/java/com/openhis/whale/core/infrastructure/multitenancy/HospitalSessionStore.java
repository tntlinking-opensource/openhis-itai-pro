/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.multitenancy;

/**
 * 用户在租户下已选医院的跨请求持久化（HIS {@code adm_organization.id}）。
 */
public interface HospitalSessionStore {

    Long get(Long userId, Long tenantId);

    void put(Long userId, Long tenantId, Long hospitalOrganizationId);

    void remove(Long userId, Long tenantId);
}
