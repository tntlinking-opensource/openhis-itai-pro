/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.position.support;

import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.whale.identity.application.RoleStaffScopeConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 岗位 API 与平台角色映射：兼容旧前端数值 roleCode，真源为 {@code whale_roles}。
 */
public final class PositionRoleApiCompat {

    /**
     * 按平台角色 code 解析主键（{@link com.openhis.health.application.shared.validator.WhaleRoleResolver} 等实现方注入）。
     */
    @FunctionalInterface
    public interface RoleIdByCodeLookup extends Function<String, Long> {
        @Override
        Long apply(String roleCode);
    }

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

    /**
     * 解析岗位 API 角色主键：优先新字段 {@code roleId}，否则按旧前端数值 {@code legacyRoleCode} 映射平台 code 再查库。
     *
     * @param roleId          平台角色主键（新 API）
     * @param legacyRoleCode  旧前端 1=医生、2=护士 等数值码
     * @param whaleRoleLookup 平台角色查表
     * @return 解析后的角色主键或 null
     */
    public static Long resolveRoleId(Long roleId, Integer legacyRoleCode, RoleIdByCodeLookup whaleRoleLookup) {
        // 新 API 已传 roleId 时直接采用
        if (roleId != null) {
            return roleId;
        }
        // 未传 legacy 码无法兼容旧前端
        if (legacyRoleCode == null) {
            return null;
        }
        String whaleCode = LEGACY_TO_WHALE_CODE.get(legacyRoleCode);
        // 未知 legacy 数值不做猜测
        if (whaleCode == null) {
            return null;
        }
        return whaleRoleLookup.apply(whaleCode);
    }

    /**
     * 将平台内置角色 id 反向映射为旧前端数值码（仅 10–14 段内置 id 有映射）。
     *
     * @param whaleRoleId 平台角色主键
     * @return legacy 数值码或 null
     */
    public static Integer toLegacyRoleCode(Long whaleRoleId) {
        if (whaleRoleId == null) {
            return null;
        }
        // 内置角色 id 与旧前端枚举一一对应
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

    /**
     * 按人员管辖范围（staffScope）推断默认岗位角色 id，供登录/岗位初始化缺省值。
     *
     * @param staffScope        {@link RoleStaffScopeConstants} 中的范围码
     * @param whaleRoleLookup   平台角色查表
     * @return 对应平台角色主键或 null
     */
    public static Long defaultRoleIdForStaffScope(String staffScope, RoleIdByCodeLookup whaleRoleLookup) {
        if (staffScope == null || whaleRoleLookup == null) {
            return null;
        }
        String whaleCode = null;
        // 各 staffScope 映射到单一平台内置角色
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
        return whaleRoleLookup.apply(whaleCode);
    }
}
