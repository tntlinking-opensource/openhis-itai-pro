/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.session;

/**
 * 用户登录会话快照（不可变，可安全放入缓存）。
 */
public record UserLoginSessionSnapshot(
        Long userId,
        Long tenantId,
        Long hospitalOrganizationId,
        Long organizationUnitId) {

    public static UserLoginSessionSnapshot absent(Long userId) {
        return new UserLoginSessionSnapshot(userId, null, null, null);
    }

    public UserLoginSessionSnapshot withoutHospitalOrganizationId() {
        return new UserLoginSessionSnapshot(userId, tenantId, null, organizationUnitId);
    }

    public UserLoginSessionSnapshot withoutOrganizationUnitId() {
        return new UserLoginSessionSnapshot(userId, tenantId, hospitalOrganizationId, null);
    }
}
