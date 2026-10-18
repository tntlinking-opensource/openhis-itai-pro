/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.validator;

import com.zxplat.domain.share.constants.PlatformRoleCodeConstants;
import com.zxplat.framework.identity.application.UserAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * 按 {@code zxplat_roles.code} 解析平台角色主键；内置角色 id 启动后缓存，供岗位查人收窄使用。
 */
@Component
@RequiredArgsConstructor
public class PlatformRoleResolver {

    /** 平台用户/角色读侧应用服务。 */
    private final UserAppService userAppService;

    /** 内置平台角色 code → id 启动后缓存（volatile 双检锁刷新）。 */
    private volatile Map<String, Long> builtInCodeToIdCache;

    /**
     * 按平台角色主键解析 {@code zxplat_roles.code}；未命中时返回 {@code null}。
     *
     * @param roleId 平台角色主键
     * @return 角色 code 或 null
     */
    public String findRoleCodeById(Long roleId) {
        return userAppService.findRoleCodeById(roleId);
    }

    /**
     * 按 {@code zxplat_roles.code} 解析平台角色主键；内置角色走启动后缓存，其余查库。
     *
     * @param roleCode 平台角色 code，空白时返回 null
     * @return 角色主键或 null
     */
    public Long findRoleIdByCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
        Long cached = builtInCodeToIdMap().get(normalized);
        if (cached != null) {
            return cached;
        }
        return userAppService.findRoleIdByCode(roleCode.trim());
    }

    /**
     * 判断岗位分配上的 roleId 是否匹配指定平台角色 code。
     *
     * @param roleId             岗位分配上的平台角色主键
     * @param platformRoleCode   期望的平台角色 code
     * @return 匹配时为 {@code true}
     */
    public boolean matchesPlatformRoleId(Long roleId, String platformRoleCode) {
        if (roleId == null || platformRoleCode == null || platformRoleCode.isBlank()) {
            return false;
        }
        Long expected = findRoleIdByCode(platformRoleCode);
        if (expected == null) {
            return false;
        }
        return roleId.equals(expected);
    }

    /**
     * 兼容旧业务语义码（如 {@code PHARMACIST}）或 {@code ROLE_*} 平台码。
     *
     * @param filterCode 业务筛选码或平台角色 code
     * @return 解析到的平台角色主键；无法映射时为 {@code null}
     */
    public Long resolvePlatformRoleId(String filterCode) {
        if (filterCode == null || filterCode.isBlank()) {
            return null;
        }
        String trimmed = filterCode.trim();
        Long direct = findRoleIdByCode(trimmed);
        if (direct != null) {
            return direct;
        }
        String legacyCode = trimmed.toUpperCase(Locale.ROOT);
        if ("DOCTOR".equals(legacyCode)) {
            return findRoleIdByCode(PlatformRoleCodeConstants.ROLE_DOCTOR);
        }
        if ("NURSE".equals(legacyCode)) {
            return findRoleIdByCode(PlatformRoleCodeConstants.ROLE_NURSE);
        }
        if ("PHARMACIST".equals(legacyCode)) {
            return findRoleIdByCode(PlatformRoleCodeConstants.ROLE_PHARMACIST);
        }
        if ("WAREHOUSE_ADMIN".equals(legacyCode)) {
            return findRoleIdByCode(PlatformRoleCodeConstants.ROLE_WAREHOUSE);
        }
        if ("ADMINISTRATIVE".equals(legacyCode)) {
            return findRoleIdByCode(PlatformRoleCodeConstants.ROLE_ACCOUNTANT);
        }
        return null;
    }

    /**
     * 懒加载内置平台角色 code → id 映射（线程安全双检锁）。
     *
     * @return 内置角色 code 到主键的不可变快照
     */
    private Map<String, Long> builtInCodeToIdMap() {
        Map<String, Long> local = builtInCodeToIdCache;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (builtInCodeToIdCache != null) {
                return builtInCodeToIdCache;
            }
            builtInCodeToIdCache = userAppService.findStaticRoleCodeToIdMap();
            return builtInCodeToIdCache;
        }
    }
}
