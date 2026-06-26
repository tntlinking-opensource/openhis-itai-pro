/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.module.common.converter.position;

import com.openhis.health.module.common.validator.WhaleRoleLookup;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.whale.identity.application.RoleStaffScopeConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * 岗位 API 与平台角色映射：兼容旧前端数值 roleCode，真源为 {@code whale_roles}。
 */
public final class PositionRoleApiCompat {

    private static final Map<Integer, String> LEGACY_TO_WHALE_CODE = new HashMap<>();

    static {
        LEGACY_TO_WHALE_CODE.put(1, WhalePlatformRoleCodeConstants.ROLE_DOCTOR);
        LEGACY_TO_WHALE_CODE.put(2, WhalePlatformRoleCodeConstants.ROLE_NURSE);
        LEGACY_TO_WHALE_CODE.put(3, WhalePlatformRoleCodeConstants.ROLE_PHARMACIST);
        LEGACY_TO_WHALE_CODE.put(4, WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE);
        LEGACY_TO_WHALE_CODE.put(6, WhalePlatformRoleCodeConstants.ROLE_ACCOUNTANT);
    }

    private PositionRoleApiCompat() {
    }

    public static Long resolveRoleId(Long roleId, Integer legacyRoleCode, WhaleRoleLookup whaleRoleLookup) {
        if (roleId != null) {
            return roleId;
        }
        if (legacyRoleCode == null) {
            return null;
        }
        String whaleCode = LEGACY_TO_WHALE_CODE.get(legacyRoleCode);
        if (whaleCode == null) {
            return null;
        }
        return whaleRoleLookup.findRoleIdByCode(whaleCode);
    }

    public static Integer toLegacyRoleCode(Long whaleRoleId) {
        if (whaleRoleId == null) {
            return null;
        }
        if (Long.valueOf(10L).equals(whaleRoleId)) {
            return 1;
        }
        if (Long.valueOf(11L).equals(whaleRoleId)) {
            return 2;
        }
        if (Long.valueOf(12L).equals(whaleRoleId)) {
            return 3;
        }
        if (Long.valueOf(13L).equals(whaleRoleId)) {
            return 4;
        }
        if (Long.valueOf(14L).equals(whaleRoleId)) {
            return 6;
        }
        return null;
    }

    public static Long defaultRoleIdForStaffScope(String staffScope, WhaleRoleLookup whaleRoleLookup) {
        if (staffScope == null || whaleRoleLookup == null) {
            return null;
        }
        String whaleCode = null;
        if (RoleStaffScopeConstants.DOCTOR_VISIT_ORG.equals(staffScope)) {
            whaleCode = WhalePlatformRoleCodeConstants.ROLE_DOCTOR;
        } else if (RoleStaffScopeConstants.WAREHOUSE_LOCATION.equals(staffScope)) {
            whaleCode = WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE;
        } else if (RoleStaffScopeConstants.PHARMACY_LOCATION.equals(staffScope)) {
            whaleCode = WhalePlatformRoleCodeConstants.ROLE_PHARMACIST;
        } else if (RoleStaffScopeConstants.NURSE_ORG.equals(staffScope)
            || RoleStaffScopeConstants.NURSE_WARD_LOCATION.equals(staffScope)) {
            whaleCode = WhalePlatformRoleCodeConstants.ROLE_NURSE;
        }
        if (whaleCode == null) {
            return null;
        }
        return whaleRoleLookup.findRoleIdByCode(whaleCode);
    }
}
