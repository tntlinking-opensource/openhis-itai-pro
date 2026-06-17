/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.common.validator;

import com.openhis.whale.identity.domain.Role;
import com.openhis.whale.identity.infrastructure.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 按 {@code whale_roles.code} 解析平台角色主键；内置角色 id 启动后缓存，供岗位查人收窄使用。
 */
@Component
@RequiredArgsConstructor
public class WhaleRoleLookup {

    private final RoleRepository roleRepository;

    private volatile Map<String, Long> builtInCodeToIdCache;

    /**
     * 解析平台角色主键；code 空白或未命中时返回 {@code null}。
     */
    /**
     * 按平台角色主键解析 {@code whale_roles.code}；未命中时返回 {@code null}。
     */
    public String findRoleCodeById(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return roleRepository.findById(roleId).map(Role::getCode).orElse(null);
    }

    public Long findRoleIdByCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
        Long cached = builtInCodeToIdMap().get(normalized);
        if (cached != null) {
            return cached;
        }
        return roleRepository.findByCode(roleCode.trim()).map(Role::getId).orElse(null);
    }

    /**
     * 判断岗位分配上的 roleId 是否匹配指定平台角色 code。
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
            return findRoleIdByCode(com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants.ROLE_DOCTOR);
        }
        if ("NURSE".equals(legacyCode)) {
            return findRoleIdByCode(com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants.ROLE_NURSE);
        }
        if ("PHARMACIST".equals(legacyCode)) {
            return findRoleIdByCode(com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants.ROLE_PHARMACIST);
        }
        if ("WAREHOUSE_ADMIN".equals(legacyCode)) {
            return findRoleIdByCode(com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE);
        }
        if ("ADMINISTRATIVE".equals(legacyCode)) {
            return findRoleIdByCode(com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants.ROLE_ACCOUNTANT);
        }
        return null;
    }

    private Map<String, Long> builtInCodeToIdMap() {
        Map<String, Long> local = builtInCodeToIdCache;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (builtInCodeToIdCache != null) {
                return builtInCodeToIdCache;
            }
            Map<String, Long> map = new HashMap<>();
            for (Role role : roleRepository.findByIsStaticTrueAndIsDeletedFalseOrderByIdAsc()) {
                if (role == null || role.getCode() == null || role.getId() == null) {
                    continue;
                }
                map.put(role.getCode().trim().toUpperCase(Locale.ROOT), role.getId());
            }
            builtInCodeToIdCache = Map.copyOf(map);
            return builtInCodeToIdCache;
        }
    }
}
